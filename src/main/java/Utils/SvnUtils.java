package Utils;

import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

import java.util.Collection;

import static Utils.PrintLog.print_error;

public class SvnUtils {

    public static final int SUCCESS = 1;
    public static final int FAILURE = 0;
    public static final int ERROR = -1;

    public static void initSvnSetup() {
        // 初始化 SVNKit
        DAVRepositoryFactory.setup();
        SVNRepositoryFactoryImpl.setup();
        FSRepositoryFactory.setup();
    }

    //检查SVN账号密码是否正常
    public static int checkAuthentication(String url, String username, String password) {
        SVNRepository repository=null;
        try {
            repository = SVNRepositoryFactory.create(SVNURL.parseURIEncoded(url));
            ISVNAuthenticationManager authManager = SVNWCUtil.createDefaultAuthenticationManager(username, password.toCharArray());
            repository.setAuthenticationManager(authManager);
            repository.getLatestRevision(); // 尝试获取最新修订号以验证认证
            return SUCCESS;
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.RA_NOT_AUTHORIZED || e.getErrorMessage().getErrorCode() == SVNErrorCode.RA_UNKNOWN_AUTH) {
                //print_error(String.format("Authentication failed: %s:%s -> %s", username, password, e.getMessage()));
                //svn: E170001: Authentication required for '<svn://x.x.x.x:x> /data/svn/reon
                return FAILURE;
            } else {
                print_error(String.format("Error On Checking Authentication: %s:%s -> %s, Should Retry...", username, password, e.getMessage()));
                return ERROR;
            }
        }finally {
            if (repository != null) {
                repository.closeSession();
            }
        }
    }

    //检查SVN存储库
    public static void SVNRepositoryShow(String svnUrl, String path, String svnUser, String svnPass) {
        // 创建 SVN 仓库对象
        SVNRepository repository = null;
        try {
            repository = SVNRepositoryFactory.create(SVNURL.parseURIEncoded(svnUrl));
        } catch (SVNException e) {
            System.err.println("Failed to create SVNRepository: " + e.getMessage());
            return;
        }

        // 设置用户名和密码
        ISVNAuthenticationManager authManager = SVNWCUtil.createDefaultAuthenticationManager(svnUser, svnPass.toCharArray());
        repository.setAuthenticationManager(authManager);

        // 列出指定目录下的文件列表
        try {
            Collection<SVNDirEntry> entries = repository.getDir(path, SVNRevision.HEAD.getNumber(), null, (Collection<SVNDirEntry>) null);

            for (SVNDirEntry entry : entries) {
                System.out.println("Name: " + entry.getName() + ", Kind: " + entry.getKind());
            }
        } catch (SVNException e) {
            System.err.println("Failed to list files: " + e.getMessage());
        } finally {
            if (repository != null) {
                repository.closeSession();
            }
        }
    }
}
