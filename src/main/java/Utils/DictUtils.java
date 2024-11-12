package Utils;

import cn.hutool.core.io.FileUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static Utils.PrintLog.print_debug;
import static Utils.PrintLog.print_error;
import static Utils.UserPassPair.splitAndCreatUserPassPairs;
import static cn.hutool.core.io.CharsetDetector.detect;

public class DictUtils {

    public static ArrayList<UserPassPair> createCartesianUserPassPairs(List<String> usernames, List<String> passwords) {
        //创建 笛卡尔积 模式的用户密码对
        HashSet<UserPassPair> userPassPairs = new HashSet<>();

        for (String username : usernames) {
            for (String password : passwords) {
                userPassPairs.add(new UserPassPair(username.trim(), password.trim()));
            }
        }
        print_debug(String.format("Create Cartesian (pairs=max[m*n]) User Pass Pairs [%s]", userPassPairs.size()));

        return new ArrayList<>(userPassPairs);
    }

    public static String getFileStrAbsolutePath(String fileStr) {
        //获取文件的物理路径
        return new File(fileStr).getAbsolutePath();
    }


    public static boolean isEmptyFile(String fileStr) {
        //判断文件是否为空
        String absolutePath = getFileStrAbsolutePath(fileStr);
        return FileUtil.isEmpty(new File(absolutePath));
    }


    public static boolean isNotEmptyFile(String fileStr) {
        //判断文件是否不为空
        return !isEmptyFile(fileStr);
    }

    public static boolean writeUserPassPairToFile(String historyFile, String separator, UserPassPair userPassPair){
        //写入账号密码对文件到历史记录文件
        try {
            historyFile = getFileStrAbsolutePath(historyFile);
            String content = String.format("%s%s%s",userPassPair.getUsername(), separator, userPassPair.getPassword());
            // 使用 Hutool 写入字符串到文件
            FileUtil.appendString(String.format("%s\n", content), historyFile, "UTF-8");
            return true;
        } catch (Exception e){
            e.printStackTrace();
            return false;
        }
    }

    public static boolean writeTitleToFile(String historyFile, String content){
        //写入账号密码对文件到历史记录文件
        try {
            historyFile = getFileStrAbsolutePath(historyFile);
            if(isEmptyFile(historyFile)){
                FileUtil.appendString(String.format("%s\n", content), historyFile, "UTF-8");
            }
            return true;
        } catch (Exception e){
            e.printStackTrace();
            return false;
        }
    }

    public static boolean writeLineToFile(String historyFile, String content){
        //写入账号密码对文件到历史记录文件
        try {
            historyFile = getFileStrAbsolutePath(historyFile);
            FileUtil.appendString(String.format("%s\n", content), historyFile, "UTF-8");
            return true;
        } catch (Exception e){
            e.printStackTrace();
            return false;
        }
    }

    public static String checkFileEncode(String absolutePath, String defaultEncode){
        //检测文件编码
        String encoding = defaultEncode;
        if (isNotEmptyFile(absolutePath)){
            try {
                encoding = detect(new File(absolutePath)).name();
                print_debug(String.format("Detect File Encoding [%s] From [%s]", encoding, absolutePath));
            } catch (Exception e){
                encoding = defaultEncode;
            }
        }
        return encoding;
    }

    public static List<String> readDictFile(String filePath) {
        // 读取文件内容到列表
        String absolutePath = getFileStrAbsolutePath(filePath);

        //判断文件是否存在
        if (isEmptyFile(absolutePath)){
            print_error(String.format("File Not Found Or Read Empty From [%s]", absolutePath));
            System.exit(0);
            return null;
        }

        //检查文件编码
        String checkEncode = checkFileEncode(absolutePath, "UTF-8");
        // 读取文件内容到列表
        List<String> baseLines = FileUtil.readLines(absolutePath, checkEncode);
        List<String> newLines = new ArrayList<>();
        for (String line : baseLines) {
            //去除空行和首尾空格
            String trimmedLine = line.trim();
            if (!trimmedLine.isEmpty()) {
                // 添加非空行到 processedLines
                newLines.add(trimmedLine);
            }
        }

        print_debug(String.format("Read Lines [%s] From File [%s]", newLines.size(), absolutePath));
        return newLines;
    }

    private static List<UserPassPair> subtractHashSet(HashSet<UserPassPair> inputUserPassPairs, HashSet<UserPassPair> hisUserPassPairs) {
        //将两个Hashset相减
        HashSet<UserPassPair> userPassPairs = new HashSet<>(inputUserPassPairs);
        for (UserPassPair pairToRemove : hisUserPassPairs) {
            userPassPairs.remove(pairToRemove); // 从结果中移除与 set2 中相同的元素
        }
        return new ArrayList<>(userPassPairs);
    }

    public static List<UserPassPair> excludeHistoryPairs(List<UserPassPair> rawUserPassPairs, String historyFile, String separator) {
        List<UserPassPair> userPassPairs = new ArrayList<>(rawUserPassPairs);
        // 将 List 转换为 HashSet
        HashSet<UserPassPair> userPassPairsSet = new HashSet<>(rawUserPassPairs);
        //处理历史账号密码对文件
        if (isNotEmptyFile(historyFile)){
            List<String> hisUserPassPairList =  readDictFile(historyFile);
            HashSet<UserPassPair> hisUserPassPairs = splitAndCreatUserPassPairs(hisUserPassPairList, separator);
            userPassPairs = subtractHashSet(userPassPairsSet, hisUserPassPairs);
        }
        return userPassPairs;
    }
}