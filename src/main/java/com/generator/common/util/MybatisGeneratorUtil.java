package com.generator.common.util;


import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.velocity.VelocityContext;
import org.mybatis.generator.api.MyBatisGenerator;
import org.mybatis.generator.config.Configuration;
import org.mybatis.generator.config.xml.ConfigurationParser;
import org.mybatis.generator.internal.DefaultShellCallback;

/**
 * 代码生成类
 */
public class MybatisGeneratorUtil {

	// generatorConfig模板路径
	private static String generatorConfig_vm = "/template/generatorConfig.vm";
	// Service模板路径
	private static String service_vm = "/template/Service.vm";
	// ServiceImpl模板路径
	private static String serviceImpl_vm = "/template/ServiceImpl.vm";

	/**
	 * 根据模板生成generatorConfig.xml文件
	 * @param jdbc_driver   驱动路径
	 * @param jdbc_url      链接
	 * @param jdbc_username 帐号
	 * @param jdbc_password 密码
	 * @param module        项目模块
	 * @param database      数据库
	 * @param table_prefix  表前缀
	 * @param package_name  包名
	 */
	public static void generator(
			String jdbc_driver,
			String jdbc_url,
			String jdbc_username,
			String jdbc_password,
			String module,
			String database,
			String table_prefix,
			String package_name,
			Map<String, String> last_insert_id_tables) throws Exception{

		generatorConfig_vm = MybatisGeneratorUtil.class.getResource(generatorConfig_vm).getPath().replaceFirst("/", "");
		service_vm = MybatisGeneratorUtil.class.getResource(service_vm).getPath().replaceFirst("/", "");
		serviceImpl_vm = MybatisGeneratorUtil.class.getResource(serviceImpl_vm).getPath().replaceFirst("/", "");

		String targetProject = module;
		String basePath = MybatisGeneratorUtil.class.getResource("/").getPath().replace("/target/classes/", "").replace(targetProject, "").replaceFirst("/", "");
		String generatorConfig_xml = MybatisGeneratorUtil.class.getResource("/").getPath().replace("/target/classes/", "") + "/src/main/resources/generatorConfig.xml";
		targetProject = basePath + targetProject;

		String servicePath = basePath + module + "/src/main/java/" + package_name.replaceAll("\\.", "/") + "/service";
		String serviceImplPath = basePath + module + "/src/main/java/" + package_name.replaceAll("\\.", "/") + "/service/impl";
		File servicedir = new File(servicePath);
		File serviceImpldir = new File(serviceImplPath);

		String sql = null;
		if (StringUtils.equals(jdbc_driver, "com.mysql.jdbc.Driver")) {
			sql = "SELECT table_name FROM INFORMATION_SCHEMA.TABLES WHERE table_schema = '" + database + "' AND table_name LIKE '" + table_prefix + "%';";
		} else {
			sql = "select table_name from user_tables where lower(table_name) like '" + table_prefix + "%'";
		}
		System.out.println(sql);
		System.out.println("========== 开始生成generatorConfig.xml文件 ==========");
		List<Map<String, Object>> tables = new ArrayList<Map<String, Object>>();
		try {
			VelocityContext context = new VelocityContext();
			Map<String, Object> table;

			// 查询定制前缀项目的所有表
			JdbcUtil jdbcUtil = new JdbcUtil(jdbc_driver, jdbc_url, jdbc_username, jdbc_password);
			List<Map<String, Object>> result = jdbcUtil.selectByParams(sql, null);
			for (Map<String, Object> map : result) {
				System.out.println(map.get("TABLE_NAME"));
				table = new HashMap<String, Object>();
				table.put("table_name", map.get("TABLE_NAME"));
				table.put("model_name", StringUtil.lineToHump(ObjectUtils.toString(map.get("TABLE_NAME"))));
				tables.add(table);
			}
			jdbcUtil.release();

			context.put("tables", tables);
			context.put("generator_javaModelGenerator_targetPackage", package_name + ".model");
			context.put("generator_sqlMapGenerator_targetPackage", "mapper");
			context.put("generator_javaClientGenerator_targetPackage", package_name + ".dao");
			context.put("targetProject", targetProject);
			context.put("targetProject_sqlMap", targetProject);
			context.put("generator_jdbc_password", jdbc_password);
			context.put("last_insert_id_tables", last_insert_id_tables);
			VelocityUtil.generate(generatorConfig_vm, generatorConfig_xml, context);
			// 删除旧代码
			deleteDir(new File(targetProject + "/src/main/java/" + package_name.replaceAll("\\.", "/") + "/model"));
			deleteDir(new File(targetProject + "/src/main/java/" + package_name.replaceAll("\\.", "/") + "/dao"));
			deleteDir(new File(targetProject + "/src/main/resources/mapper"));
			// 删除时先删除 serviceImpl 目录
			deleteDir(serviceImpldir);
			deleteDir(servicedir);
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("========== 结束生成generatorConfig.xml文件 ==========");

		System.out.println("========== 开始运行MybatisGenerator ==========");
		List<String> warnings = new ArrayList<String>();
		File configFile = new File(generatorConfig_xml);
		ConfigurationParser cp = new ConfigurationParser(warnings);
		Configuration config = cp.parseConfiguration(configFile);
		DefaultShellCallback callback = new DefaultShellCallback(true);
		MyBatisGenerator myBatisGenerator = new MyBatisGenerator(config, callback, warnings);
		myBatisGenerator.generate(null);
		for (String warning : warnings) {
			System.out.println(warning);
		}
		System.out.println("========== 结束运行MybatisGenerator ==========");

		System.out.println("========== 开始生成Service ==========");
		String ctime = new SimpleDateFormat("yyyy/M/d").format(new Date());
		// 创建时先创建 service 目录
		mkdirDir(servicedir);
		mkdirDir(serviceImpldir);
		for (int i = 0; i < tables.size(); i++) {
			String model = StringUtil.lineToHump(ObjectUtils.toString(tables.get(i).get("table_name")));
			String service = servicePath + "/" + model + "Service.java";
			String serviceImpl = serviceImplPath + "/" + model + "ServiceImpl.java";
			// 生成service
			File serviceFile = new File(service);
			if (!serviceFile.exists()) {
				VelocityContext context = new VelocityContext();
				context.put("package_name", package_name);
				context.put("model", model);
				context.put("ctime", ctime);
				VelocityUtil.generate(service_vm, service, context);
				System.out.println("新增 service 文件 " + service);
			}
			// 生成serviceImpl
			File serviceImplFile = new File(serviceImpl);
			if (!serviceImplFile.exists()) {
				VelocityContext context = new VelocityContext();
				context.put("package_name", package_name);
				context.put("model", model);
				context.put("mapper", StringUtil.toLowerCaseFirstOne(model));
				context.put("ctime", ctime);
				VelocityUtil.generate(serviceImpl_vm, serviceImpl, context);
				System.out.println("新增 serviceImpl 文件 " + serviceImpl);
			}
		}
		System.out.println("========== 结束生成Service ==========");
	}

	/**
	 * 递归删除非空文件夹
	 * @param dir
	 */
	public static void deleteDir(File dir) {
		if (dir.isDirectory()) {
			File[] files = dir.listFiles();
			for (int i = 0; i < files.length; i++) {
				deleteDir(files[i]);
			}
		}
		boolean flag =  dir.delete();
		System.out.println("删除非空文件夹 " + flag);
	}

	/**
	 * 创建文件夹
	 * @param dir
	 */
	public static void mkdirDir(File dir) {
		if (!dir.exists()) {
			boolean flag = dir.mkdir();
			System.out.println("创建文件夹 " + flag);
		}
	}
}
