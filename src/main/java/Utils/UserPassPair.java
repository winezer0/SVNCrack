package Utils;


import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import static Utils.DictUtils.isNotEmptyFile;
import static Utils.DictUtils.readDictFile;
import static Utils.PrintLog.print_debug;
import static cn.hutool.core.util.StrUtil.isEmptyIfStr;

public class UserPassPair {
    private String username;
    private String password;

    public UserPassPair(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return this.username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return this.password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    // 需要重写 hashCode 和 equals 方法以便于 HashSet 正确比较 UserPassPair 对象
    @Override
    public int hashCode() {
        return Objects.hash(username, password);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserPassPair that = (UserPassPair) o;
        return username.equals(that.username) && password.equals(that.password);
    }


    @Override
    public String toString() {
        return String.format("%s:%s",username, password);
    }

    public String toString(String separator) {
        return String.format("%s%s%s",username,separator, password);
    }


    public static List<UserPassPair> replaceUserMarkInPass(List<UserPassPair> userPassPairs, String userMark) {
        //仅处理存在的情况
        if(isEmptyIfStr(userMark) || userPassPairs.size()< 1){
            return userPassPairs;
        }

        //替换密码中的用户名标记符号为用户名
        for (UserPassPair pair : userPassPairs) {
            String newPassword = pair.getPassword().replace(userMark, pair.getUsername());
            pair.setPassword(newPassword);
        }
        return userPassPairs;
    }

    public static HashSet<UserPassPair> splitAndCreatUserPassPairs(List<String> pairStringList, String pair_separator) {
        //拆分账号密钥对文件 到用户名密码字典
        HashSet<UserPassPair> userPassPairs = new HashSet<>();
        for (String str : pairStringList) {
            // 使用 split 方法按冒号分割字符串
            String[] parts = str.split(pair_separator, 2);
            if (parts.length == 2) {
                String username = parts[0];
                String password = parts[1];
                userPassPairs.add(new UserPassPair(username.trim(), password.trim()));
            }
        }
        print_debug(String.format("Split And Creat User Pass Pairs [%s]", userPassPairs.size()));
        return userPassPairs;
    }



}
