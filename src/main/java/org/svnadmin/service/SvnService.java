package org.svnadmin.service;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Service;
import org.svnadmin.Constants;
import org.svnadmin.dao.PjAuthDao;
import org.svnadmin.dao.PjDao;
import org.svnadmin.dao.PjGrDao;
import org.svnadmin.dao.PjGrUsrDao;
import org.svnadmin.dao.UsrDao;
import org.svnadmin.entity.Pj;
import org.svnadmin.entity.PjAuth;
import org.svnadmin.entity.PjGr;
import org.svnadmin.entity.PjGrUsr;
import org.svnadmin.entity.Usr;
import org.svnadmin.util.EncryptUtil;

/**
 * 导出svn配置信息
 * 
 * @author Harvey
 * 
 */
@Service(SvnService.BEAN_NAME)
public class SvnService {
	/**
	 * Bean名称
	 */
	public static final String BEAN_NAME = "svnService";

	/**
	 * 分隔符
	 */
	private static final String SEP = System.getProperty("line.separator");
	/**
	 * 日志
	 */
	private final Logger LOG = Logger.getLogger(this.getClass());

	/**
	 * 项目DAO
	 */
	@Resource(name = PjDao.BEAN_NAME)
	PjDao pjDao;

	/**
	 * 用户DAO
	 */
	@Resource(name = UsrDao.BEAN_NAME)
	UsrDao usrDao;

	/**
	 * 项目组DAO
	 */
	@Resource(name = PjGrDao.BEAN_NAME)
	PjGrDao pjGrDao;

	/**
	 * 项目组用户DAO
	 */
	@Resource(name = PjGrUsrDao.BEAN_NAME)
	PjGrUsrDao pjGrUsrDao;

	/**
	 * 项目权限DAO
	 */
	@Resource(name = PjAuthDao.BEAN_NAME)
	PjAuthDao pjAuthDao;

	/**
	 * 导出到配置文件
	 * 
	 * @param pj
	 *            项目id
	 */
	public synchronized void exportConfig(String pj) {
		this.exportConfig(this.pjDao.get(pj));
	}

	/**
	 * 导出到配置文件
	 * 
	 * @param pj
	 *            项目
	 */
	public synchronized void exportConfig(Pj pj) {
		if (pj == null) {
			return;
		}
		File parent = new File(pj.getPath());
		if (!parent.exists() || !parent.isDirectory()) {
			throw new RuntimeException("找不到仓库 " + pj.getPath());
		}

		if (Constants.HTTP.equalsIgnoreCase(pj.getType())) {// HTTP(单库) SVNPath
			this.exportHTTP(pj);
		} else if (Constants.HTTP_MUTIL.equalsIgnoreCase(pj.getType())) {// HTTP(多库)
																			// SVNParentPath
			this.exportHTTPMutil(pj);
		} else if (Constants.SVN.equalsIgnoreCase(pj.getType())) {// SVN
			this.exportSVN(pj);
		}

	}

	/**
	 * 导出svn协议的配置信息
	 * 
	 * @param pj
	 *            项目
	 */
	private void exportSVN(Pj pj) {
		// 项目的用户
		List<Usr> usrList = this.usrDao.getList(pj.getPj());
		// 项目的用户组
		List<PjGr> pjGrList = this.getPjGrList(pj.getPj());
		// 项目的权限
		Map<String, List<PjAuth>> pjAuthMap = this.getPjAuthList(pj.getPj());

		this.exportSvnConf(pj);
		this.exportPasswdSVN(pj, usrList);
		this.exportAuthz(pj, pjGrList, pjAuthMap);
	}

	/**
	 * 导出http(单库)的配置信息
	 * 
	 * @param pj
	 *            项目
	 */
	private void exportHTTP(Pj pj) {
		// 项目的用户
		List<Usr> usrList = this.usrDao.getList(pj.getPj());
		// 项目的用户组
		List<PjGr> pjGrList = this.getPjGrList(pj.getPj());
		// 项目的权限
		Map<String, List<PjAuth>> pjAuthMap = this.getPjAuthList(pj.getPj());

		this.exportSVNPathConf(pj);
		this.exportPasswdHTTP(pj, usrList);
		this.exportAuthz(pj, pjGrList, pjAuthMap);
	}

	/**
	 * 导出http(多库)的配置信息
	 * 
	 * @param pj
	 *            项目
	 */
	private void exportHTTPMutil(Pj pj) {
		File root = new File(pj.getPath()).getParentFile();
		String svnRoot = StringUtils.replace(root.getAbsolutePath(), "\\", "/");
		if (!svnRoot.endsWith("/")) {
			svnRoot += "/";
		}
		// 和这个项目在同一个父目录的所有项目的用户
		List<Usr> usrList = this.usrDao.getListByRootPath(svnRoot);
		// 和这个项目在同一个父目录的所有项目的用户组
		List<PjGr> pjGrList = this.getPjGrListByRootPath(svnRoot);
		// 和这个项目在同一个父目录的所有项目的权限
		Map<String, List<PjAuth>> pjAuthMap = this
				.getPjAuthListByRootPath(svnRoot);

		this.exportSVNParentPathConf(root);

		this.exportPasswdHTTPMutil(root, usrList);

		this.exportAuthzHTTPMutil(root, pjGrList, pjAuthMap);
	}

	/**
	 * 获取有相同svn root的项目的权限列表
	 * 
	 * @param rootPath
	 *            svn root
	 * @return 有相同svn root的项目的权限列表
	 */
	private Map<String, List<PjAuth>> getPjAuthListByRootPath(String rootPath) {
		Map<String, List<PjAuth>> results = new LinkedHashMap<String, List<PjAuth>>();// <res,List<PjAuth>>
		List<PjAuth> pjAuthList = this.pjAuthDao.getListByRootPath(rootPath);
		// 格式化返回数据
		for (PjAuth pjAuth : pjAuthList) {
			List<PjAuth> authList = results.get(pjAuth.getRes());
			if (authList == null) {
				authList = new ArrayList<PjAuth>();
				results.put(pjAuth.getRes(), authList);
			}
			authList.add(pjAuth);

		}
		return results;
	}

	/**
	 * 获取项目的权限列表
	 * 
	 * @param pj
	 *            项目
	 * @return 项目的权限列表
	 */
	private Map<String, List<PjAuth>> getPjAuthList(String pj) {
		Map<String, List<PjAuth>> results = new LinkedHashMap<String, List<PjAuth>>();// <res,List<PjAuth>>
		List<PjAuth> pjAuthList = this.pjAuthDao.getList(pj);
		// 格式化返回数据
		for (PjAuth pjAuth : pjAuthList) {
			List<PjAuth> authList = results.get(pjAuth.getRes());
			if (authList == null) {
				authList = new ArrayList<PjAuth>();
				results.put(pjAuth.getRes(), authList);
			}
			authList.add(pjAuth);

		}
		return results;
	}

	/**
	 * 获取项目的组列表
	 * 
	 * @param pj
	 *            项目
	 * @return 项目的组列表
	 */
	private List<PjGr> getPjGrList(String pj) {

		List<PjGr> results = this.pjGrDao.getList(pj);
		if (results != null) {
			// 组的用户
			for (PjGr pjGr : results) {
				List<PjGrUsr> pjGrUsrs = this.pjGrUsrDao.getList(pjGr.getPj(),
						pjGr.getGr());
				if (pjGrUsrs != null) {
					pjGr.setPjGrUsrs(pjGrUsrs);
				}
			}
		}

		return results;
	}

	/**
	 * 获取有相同svn root的项目的权限列表
	 * 
	 * @param rootPath
	 *            svn root
	 * @return 有相同svn root的项目的权限列表
	 */
	private List<PjGr> getPjGrListByRootPath(String rootPath) {

		List<PjGr> results = this.pjGrDao.getListByRootPath(rootPath);
		if (results != null) {
			// 组的用户
			for (PjGr pjGr : results) {
				List<PjGrUsr> pjGrUsrs = this.pjGrUsrDao.getList(pjGr.getPj(),
						pjGr.getGr());
				if (pjGrUsrs != null) {
					pjGr.setPjGrUsrs(pjGrUsrs);
				}
			}
		}

		return results;
	}

	/**
	 * 输出http多库方式的密码文件
	 * 
	 * @param root
	 *            svn root
	 * @param usrList
	 *            所有用户列表
	 */
	private void exportPasswdHTTPMutil(File root, List<Usr> usrList) {
		File outFile = new File(root, "passwd.http");
		StringBuffer contents = new StringBuffer();

		for (Usr usr : usrList) {
			// 采用SHA加密
			// http://httpd.apache.org/docs/2.2/misc/password_encryptions.html
			String shaPsw = "{SHA}"
					+ EncryptUtil
							.encriptSHA1(EncryptUtil.decrypt(usr.getPsw()));
			contents.append(usr.getUsr()).append(":").append(shaPsw)
					.append(SEP);
		}
		this.write(outFile, contents.toString());
	}

	/**
	 * 输出http单库方式的密码文件
	 * 
	 * @param pj
	 *            项目
	 * @param usrList
	 *            项目用户列表
	 */
	private void exportPasswdHTTP(Pj pj, List<Usr> usrList) {
		File outFile = new File(pj.getPath(), "/conf/passwd.http");
		StringBuffer contents = new StringBuffer();

		for (Usr usr : usrList) {
			// 采用SHA加密
			// http://httpd.apache.org/docs/2.2/misc/password_encryptions.html
			String shaPsw = "{SHA}"
					+ EncryptUtil
							.encriptSHA1(EncryptUtil.decrypt(usr.getPsw()));
			contents.append(usr.getUsr()).append(":").append(shaPsw)
					.append(SEP);
		}
		this.write(outFile, contents.toString());
	}

	/**
	 * 输出svn方式的密码文件
	 * 
	 * @param pj
	 *            项目
	 * @param usrList
	 *            项目用户列表
	 */
	private void exportPasswdSVN(Pj pj, List<Usr> usrList) {
		File outFile = new File(pj.getPath(), "/conf/passwd");
		StringBuffer contents = new StringBuffer();
		contents.append("[users]").append(SEP);

		for (Usr usr : usrList) {
			contents.append(usr.getUsr()).append("=")
					.append(EncryptUtil.decrypt(usr.getPsw())).append(SEP);// 解密
		}
		this.write(outFile, contents.toString());
	}

	/**
	 * 输出http多库方式的权限文件
	 * 
	 * @param root
	 *            svn root
	 * @param pjGrList
	 *            所有的项目列表
	 * @param resMap
	 *            所有的权限列表
	 */
	private void exportAuthzHTTPMutil(File root, List<PjGr> pjGrList,
			Map<String, List<PjAuth>> resMap) {
		if (root == null) {
			return;
		}
		/*
		 * if(pjGrList == null || pjGrList.size() == 0){ return; } if(pjAuthMap
		 * == null || pjAuthMap.size() == 0){ return; }
		 */
		File outFile = new File(root, "authz");
		StringBuffer contents = new StringBuffer();
		contents.append("[aliases]").append(SEP);
		contents.append("[groups]").append(SEP);

		for (PjGr pjGr : pjGrList) {
			contents.append(pjGr.getGr()).append("=");
			for (int i = 0; i < pjGr.getPjGrUsrs().size(); i++) {
				if (i != 0) {
					contents.append(",");
				}
				contents.append(pjGr.getPjGrUsrs().get(i).getUsr());
			}
			contents.append(SEP);
		}

		contents.append(SEP);

		for (Iterator<String> resIterator = resMap.keySet().iterator(); resIterator
				.hasNext();) {
			String res = resIterator.next();
			contents.append(res).append(SEP);
			for (PjAuth pjAuth : resMap.get(res)) {
				if (StringUtils.isNotBlank(pjAuth.getGr())) {
					contents.append("@").append(pjAuth.getGr()).append("=")
							.append(pjAuth.getRw()).append(SEP);
				} else if (StringUtils.isNotBlank(pjAuth.getUsr())) {
					contents.append(pjAuth.getUsr()).append("=")
							.append(pjAuth.getRw()).append(SEP);
				}
			}
			contents.append(SEP);
		}

		this.write(outFile, contents.toString());
	}

	/**
	 * 输出权限配置文件
	 * 
	 * @param pj
	 *            项目
	 * @param pjGrList
	 *            项目的组列表
	 * @param resMap
	 *            项目的权限列表
	 */
	private void exportAuthz(Pj pj, List<PjGr> pjGrList,
			Map<String, List<PjAuth>> resMap) {
		if (pj == null || StringUtils.isBlank(pj.getPj())) {
			return;
		}
		/*
		 * if(pjGrList == null || pjGrList.size() == 0){ return; } if(pjAuthMap
		 * == null || pjAuthMap.size() == 0){ return; }
		 */
		File outFile = new File(pj.getPath(), "/conf/authz");
		StringBuffer contents = new StringBuffer();
		contents.append("[aliases]").append(SEP);
		contents.append("[groups]").append(SEP);

		for (PjGr pjGr : pjGrList) {
			contents.append(pjGr.getGr()).append("=");
			for (int i = 0; i < pjGr.getPjGrUsrs().size(); i++) {
				if (i != 0) {
					contents.append(",");
				}
				contents.append(pjGr.getPjGrUsrs().get(i).getUsr());
			}
			contents.append(SEP);
		}
		
		contents.append(SEP);

		for (Iterator<String> resIterator = resMap.keySet().iterator(); resIterator
				.hasNext();) {
			String res = resIterator.next();
			contents.append(res).append(SEP);
			for (PjAuth pjAuth : resMap.get(res)) {
				if (StringUtils.isNotBlank(pjAuth.getGr())) {
					contents.append("@").append(pjAuth.getGr()).append("=")
							.append(pjAuth.getRw()).append(SEP);
				} else if (StringUtils.isNotBlank(pjAuth.getUsr())) {
					contents.append(pjAuth.getUsr()).append("=")
							.append(pjAuth.getRw()).append(SEP);
				}
			}
			contents.append(SEP);
		}

		this.write(outFile, contents.toString());
	}

	/**
	 * 输出svn方式的svnserve.conf
	 * 
	 * @param pj
	 *            项目
	 */
	private void exportSvnConf(Pj pj) {
		if (pj == null || StringUtils.isBlank(pj.getPj())) {
			return;
		}
		File outFile = new File(pj.getPath(), "/conf/svnserve.conf");

		StringBuffer contents = new StringBuffer();
		contents.append("[general]").append(SEP);
		contents.append("anon-access = none").append(SEP);
		contents.append("auth-access = write").append(SEP);
		contents.append("password-db = passwd").append(SEP);
		contents.append("authz-db = authz").append(SEP);
		contents.append("[sasl]").append(SEP);
		this.write(outFile, contents.toString());

	}

	/**
	 * 输出http单库方式的httpd.conf文件
	 * 
	 * @param pj
	 *            项目
	 */
	private void exportSVNPathConf(Pj pj) {
		if (pj == null || StringUtils.isBlank(pj.getPj())) {
			return;
		}
		File outFile = new File(pj.getPath(), "/conf/httpd.conf");
		StringBuffer contents = new StringBuffer();
		contents.append("#Include ").append(pj.getPath())
				.append("/conf/httpd.conf").append(SEP);

		String location = pj.getPj();
		// 例如 http://192.168.1.100/svn/projar
		if (StringUtils.isNotBlank(pj.getUrl())
				&& pj.getUrl().indexOf("//") != -1) {
			location = StringUtils.substringAfter(pj.getUrl(), "//");// 192.168.1.100/svn/projar
			location = StringUtils.substringAfter(location, "/");// svn/projar
		}

		contents.append("<Location /").append(location).append(">").append(SEP);
		contents.append("DAV svn").append(SEP);
		contents.append("SVNPath ").append(pj.getPath()).append(SEP);
		contents.append("AuthType Basic").append(SEP);
		contents.append("AuthName ").append("\"").append(pj.getPj())
				.append("\"").append(SEP);
		contents.append("AuthUserFile ").append(pj.getPath())
				.append("/conf/passwd.http").append(SEP);
		contents.append("AuthzSVNAccessFile ").append(pj.getPath())
				.append("/conf/authz").append(SEP);
		contents.append("Require valid-user").append(SEP);
		contents.append("</Location>").append(SEP);
		this.write(outFile, contents.toString());

	}

	/**
	 * 输出http多库方式的httpd.conf文件
	 * 
	 * @param root
	 *            svn root
	 */
	private void exportSVNParentPathConf(File root) {
		String svnRoot = StringUtils.replace(root.getAbsolutePath(), "\\", "/");
		File outFile = new File(root, "httpd.conf");
		StringBuffer contents = new StringBuffer();
		contents.append("#Include ").append(svnRoot).append("/httpd.conf")
				.append(SEP);

		String location = root.getName();

		contents.append("<Location /").append(location).append("/>")
				.append(SEP);
		contents.append("DAV svn").append(SEP);
		contents.append("SVNListParentPath on").append(SEP);
		contents.append("SVNParentPath ").append(svnRoot).append(SEP);
		contents.append("AuthType Basic").append(SEP);
		contents.append("AuthName ").append("\"")
				.append("Subversion repositories").append("\"").append(SEP);
		contents.append("AuthUserFile ").append(svnRoot).append("/passwd.http")
				.append(SEP);
		contents.append("AuthzSVNAccessFile ").append(svnRoot).append("/authz")
				.append(SEP);
		contents.append("Require valid-user").append(SEP);
		contents.append("</Location>").append(SEP);
		contents.append("RedirectMatch ^(/").append(location).append(")$ $1/")
				.append(SEP);
		this.write(outFile, contents.toString());
	}

	/**
	 * 写文件流
	 * 
	 * @param outFile
	 *            输出文件
	 * @param contents
	 *            内容
	 */
	private void write(File outFile, String contents) {
		BufferedWriter writer = null;
		try {
			if (contents == null) {
				contents = "";
			}
			if (!outFile.getParentFile().exists()) {
				outFile.getParentFile().mkdirs();
			}
			writer = new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream(outFile), "UTF-8"));// UTF-8 without
																// BOM
			writer.write(contents);
			LOG.debug(outFile);
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e.getMessage());
		} finally {
			if (writer != null) {
				try {
					writer.flush();
				} catch (IOException e) {
					e.printStackTrace();
				}
				try {
					writer.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
}