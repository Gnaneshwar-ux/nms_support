package com.nms.support.nms_support.service.buildTabPack.patchUpdate;

import java.awt.image.BufferedImage;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.security.cert.X509Certificate;
import java.util.concurrent.CountDownLatch;

import javax.imageio.ImageIO;
import javax.net.ssl.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import com.nms.support.nms_support.controller.BuildAutomation;
import com.nms.support.nms_support.model.ProjectEntity;
import com.nms.support.nms_support.service.globalPack.DialogUtil;
import com.nms.support.nms_support.service.globalPack.ManageFile;
import javafx.application.Platform;
import javafx.scene.control.TextInputDialog;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class CreateInstallerCommand {
	private String dir_temp = "C:\\\\Oracle NMS\\\\results";
	private String installer_loc = "C:\\Oracle NMS\\results";
	private static final String NSIS_EXE = "makensisw.exe"; // Update with actual path
	private static final String LAUNCH4J_EXE = "launch4jc.exe"; // Update with actual path
	private static final String ICON = "logo16.ico";
	private boolean includeJRE = false;
	private Document launch4jDoc;
	private static final String[] EXTRA_JARS = new String[0];
	private static final String TEMPLATE = "Section \"%long%\"\n" + "  SetShellVarContext all\n"
			+ "  CreateShortCut \"$SMPROGRAMS\\${APP_NAME}\\%long%.lnk\" \"$INSTDIR\\%short%.exe\"\n"
			+ "  File %short%.exe\n" + "SectionEnd\n" + "\n" + "Section \"un.%long%\"\n" + "  SetShellVarContext all\n"
			+ "    Delete \"$SMPROGRAMS\\${APP_NAME}\\%long%.lnk\"\n" + "    Delete \"$INSTDIR\\%short%.exe\"\n"
			+ "SectionEnd\n" + "\n";

	private static final String JRE_TEMPLATE = "Section \"-jre\"\n" + "    File /r /x .svn \"%JRE%\"\n" + "SectionEnd\n"
			+ "\n" + "Section \"-un.jre\"\n" + "    RMDir /r \"$INSTDIR\\%JRE_DEST%\"\n" + "SectionEnd\n";

	private static BuildAutomation buildAutomation;

	public void execute(String appURL, String envVarName, ProjectEntity project, BuildAutomation buildAutomation) throws Exception {

		this.buildAutomation = buildAutomation;


        if(envVarName == null || envVarName.isEmpty()){
			buildAutomation.appendTextToLog("ENV provided is invalid");
			return;
		}
		else {
			if(!doesEnvVariableExist(envVarName)){
				buildAutomation.appendTextToLog("Provided ENV VAR not exists("+ envVarName +"). Exiting Upgrade process");
				return;
			}
		}

		dir_temp = project.getExePath(); // where all the installer and extracted java folder is placed
		installer_loc = project.getExePath();
		String serverURL = adjustUrl(appURL);
		boolean state =cleanDirectory(Path.of(dir_temp));
		if(!state)return;
     	buildAutomation.appendTextToLog("Loading Resources..");

		FileFetcher.loadResources(dir_temp,serverURL, buildAutomation);

		SFTPDownloadAndUnzip.start(dir_temp, project, buildAutomation);


		try {
			processDirectory(dir_temp);
		} catch (IOException e) {
			e.printStackTrace();
		}

		String currentDir = System.getProperty("user.dir");

		// Print the current working directory
		buildAutomation.appendTextToLog("Current Directory: " + currentDir);
		// SSL certificate handling

		URL url = new URL(serverURL);

		// Bypass SSL certificate verification for development purposes
		SSLContext sslContext = SSLContext.getInstance("TLS");
		sslContext.init(null, new TrustManager[] { new X509TrustManager() {
			@Override
			public X509Certificate[] getAcceptedIssuers() {
				return null;
			}

			@Override
			public void checkClientTrusted(X509Certificate[] certs, String authType) {
			}

			@Override
			public void checkServerTrusted(X509Certificate[] certs, String authType) {
			}
		} }, new java.security.SecureRandom());

		HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
		HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);

		// End of SSL certificate handling

		String defaultName = "Oracle_NMS_" + url.getHost() + "_" + url.getPort() + ".exe";
		File installerLocation = getInstallerLocation(defaultName);

		// Update based on your requirement
		File dir = (new File(dir_temp));

		buildAutomation.appendTextToLog("dir - " + dir.toString());

		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setAttribute(XMLConstants.FEATURE_SECURE_PROCESSING, Boolean.TRUE);
		DocumentBuilder docBuilder = factory.newDocumentBuilder();

		Properties props = new Properties();
		try (InputStream propsIS = new FileInputStream(dir_temp + "/license.properties")) { // Update with actual path
			props.load(propsIS);
		}

		String productsStr = props.getProperty("LicensedProducts");
		String[] products = productsStr.split(", *");
		products = Arrays.stream(products) // Convert the array to a stream
				.filter(product -> !product.trim().equals("FlexOperations")) // Filter out "FlexOperations"
				.toArray(String[]::new); // Collect the result back into an array
		StringBuilder str = new StringBuilder();
		String jreDestination = null;
		if (includeJRE) {
			jreDestination = new File(getJRELocation()).getName();
			String jreSection = JRE_TEMPLATE.replace("%JRE%", getJRELocation()).replace("%JRE_DEST%", jreDestination);
			str.append(jreSection);
			File jreDestinationDir = new File("current_dir_temp/jre");

			try {
				copyDirectory(new File(getJRELocation()), new File(dir_temp + "/jre"));
				buildAutomation.appendTextToLog("JRE directory copied successfully.");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		for (String product : products) {
			this.launch4jDoc = docBuilder.newDocument();
			Element launch4j = addElement(this.launch4jDoc, "launch4jConfig", (String) null);

			String name = props.getProperty(product);
			String jnlpName = props.getProperty(product + ".jnlpName", product);
			String iUrl = serverURL + jnlpName + ".jnlp";
			String saveClasspath = null;
			try (InputStream is = getInputStreamFromURL(iUrl)) {
				str.append(TEMPLATE.replace("%long%", name).replace("%short%", jnlpName));
				Document document = docBuilder.parse(is);

				boolean inSystemProps = false;
				ArrayList<String> systemProps = new ArrayList<>();
				NodeList list = document.getElementsByTagName("argument");
				String maxMemory = null;
				boolean arch32 = "32".equals(System.getProperty("sun.arch.data.model"));

				for (int i = 0; i < list.getLength(); i++) {
					Node node = list.item(i);
					String value = node.getTextContent();
					if (value.equals("-p")) {
						inSystemProps = !inSystemProps;

					} else if (inSystemProps) {
						String setting = list.item(++i).getTextContent();
						if (value.equals("nms.maxmemory") && maxMemory == null) {
							maxMemory = "-Xmx" + setting;
						} else if (arch32 && value.equals("nms.maxmemory32")) {
							maxMemory = "-Xmx" + setting;
						} else if (value.startsWith("-")) {
							systemProps.add(value);
						} else {
							systemProps.add("-D" + value + "=\"" + setting + "\"");
						}
					} else {
						saveClasspath = value;
					}
				}
				if (maxMemory != null) {
					systemProps.add(maxMemory);
				}
				systemProps.add("-Dnms.application_name=" + jnlpName);
				systemProps.add("-DHOST_NAME=" + url.getHost());
				systemProps.add("-DHOST_PORT=" + url.getPort());
				Element jre = addElement(launch4j, "jre", (String) null);
				Element classPath = addElement(launch4j, "classPath", (String) null);

				addElement(launch4j, "customProcName", includeJRE ? "true" : "false");
				if (includeJRE) {
					addElement(jre, "path", jreDestination);
					addElement(jre, "jdkPreference", "jdkOnly");
				} else {

					addElement(jre, "jdkPreference", "preferJre");
				}

				list = document.getElementsByTagName("icon");
				buildAutomation.appendTextToLog(list.toString());
				(new File(dir, "images")).mkdir();
				for (int j = 0; j < list.getLength(); j++) {
					Element elem = (Element) list.item(j);
					String href = elem.getAttribute("href");
					buildAutomation.appendTextToLog(" href - " + href);
					String imageName = (new File(Utils.validatePath(href))).getName();

					String kind = elem.getAttribute("kind");
					if (kind.equals("splash")) {
						try (InputStream iconStream = getInputStreamFromURL(serverURL + href)) {
							if (iconStream == null) {
								continue;
							}
							BufferedImage image = ImageIO.read(iconStream);
							imageName = imageName.substring(0, imageName.lastIndexOf(".")) + ".bmp";
							File imageFile = new File(dir, "images/" + Utils.validatePath(imageName));
							ImageIO.write(image, "bmp", imageFile);
							Element splash = addElement(launch4j, "splash", null);
							addElement(splash, "waitForWindow", "true");
							addElement(splash, "timeout", "60");
							addElement(splash, "file", imageFile.toString());
						}

					}
				}

				File file = new File(dir, ICON);

				addElement(launch4j, "icon", file.getPath());

				addElement(launch4j, "dontWrapJar", "true");
				addElement(launch4j, "headerType", "gui");

				addElement(launch4j, "outfile", (new File(dir, jnlpName + ".exe")).getPath());
				ArrayList<String> cpList = new ArrayList<>();
				cpList.add("java/working/config");
				for (String jar : saveClasspath.split("\\;")) {
					cpList.add("nmslib/" + jar);
				}
				for (String cp : cpList) {
					addElement(classPath, "cp", cp);
				}

				addElement(classPath, "cp", "nmslib/wlthint3client.jar");
				Element app = (Element) document.getElementsByTagName("application-desc").item(0);
				addElement(classPath, "mainClass", "com.splwg.oms.fcp.JWSLauncher");
				addElement(jre, "minVersion", "1.8.0");
				for (String opt : systemProps) {
					addElement(jre, "opt", opt);
				}
				TransformerFactory transfac = TransformerFactory.newInstance();
				Utils.secureFactory(transfac);
				Transformer trans = transfac.newTransformer();
				trans.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
				trans.setOutputProperty(OutputKeys.INDENT, "yes");
				File launchXML = new File(dir, product + ".xml");
				try (FileOutputStream fos = new FileOutputStream(launchXML);
						OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
					StreamResult result = new StreamResult(writer);
					DOMSource source = new DOMSource(launch4jDoc);
					trans.transform(source, result);
				}
				runLaunch4j(launchXML);
				 launchXML.delete();
			} catch (Exception e) {
				buildAutomation.appendTextToLog("Not able to setup " + product);
				e.printStackTrace();
			}
		}
		String config = readFileAsString(dir_temp + "/nms.nsi");
		String appName = "Oracle Utilities NMS";

		config = config.replace("%INSTALLER_LOC%", installerLocation.getAbsolutePath()).replace("%name%", appName)
				.replace(";### COMPONENTS ###", str.toString());

		PrintStream ps = new PrintStream(new File(dir, "nms.nsi"), StandardCharsets.UTF_8.name());
		ps.append(config);
		ps.close();

//		for (String jarFile : EXTRA_JARS) {
//			InputStream uis = null;
//			try {
//				uis = getInputStreamFromURL(serverURL + jarFile);
//				File jarF = new File(dir, "nmslib/" + jarFile);
//				Utils.copyInputStream(uis, jarF);
//			} finally {
//				if (uis != null)
//					uis.close();
//			}
//		}

		File jarF = new File(dir, "nmslib/tools.jar");
		File toolFile = new File(System.getenv("JAVA_HOME") + "/lib/tools.jar");
		if (!toolFile.isFile()) {
			throw new Exception("JDK_NOT_FOUND");
		}
		InputStream uis = null;
		try {
			uis = new FileInputStream(toolFile);
			Utils.copyInputStream(uis, jarF);
		} finally {
			if (uis != null) {
				uis.close();
				uis = null;
			}
		}
		File f = new File(System.getenv("JAVA_HOME") + "/jre/bin/attach.dll");
		try {
			uis = new FileInputStream(f);
			Utils.copyInputStream(uis, new File(dir, "attach.dll"));
		} finally {
			if (uis != null) {
				uis.close();
				uis = null;
			}
		}
		//runNSIS(dir); //launches packed installer

		ManageFile.replaceTextInFiles(List.of(dir_temp+"/java/ant/build.properties"),"NMS_HOME", envVarName);
		ManageFile.replaceTextInFiles(List.of(dir_temp+"/java/ant/build.xml"),"NMS_HOME", envVarName);

	}

	private boolean createEnvVar(String variableName, String variableValue){
		try {

			// Command to set the environment variable
			String command = "setx " + variableName + " " + variableValue;

			// Execute the command
			ProcessBuilder processBuilder = new ProcessBuilder("cmd.exe", "/c", command);
			Process process = processBuilder.start();
			int exitCode = process.waitFor();

			if (exitCode == 0) {
				System.out.println("System variable set successfully.");
				return true;
			} else {
				System.out.println("Failed to set system variable. Exit code: " + exitCode);
				return false;
			}
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
			return false;
		}
	}

	public static boolean doesEnvVariableExist(String varName) {
		// Get the map of environment variables
		Map<String, String> env = System.getenv();
		// Check if the map contains the specified variable name
		//System.out.println(env.toString());
		return env.containsKey(varName);
	}

	private File getInstallerLocation(String defaultName) {
		// Replace with actual implementation
		return new File(installer_loc, defaultName); // Update with actual path
	}

	private String getJRELocation() throws Exception {
		File file = new File(System.getenv("JAVA_HOME") + "/jre");
		if (!file.isDirectory()) {
			throw new Exception("JDK_NOT_FOUND");
		}
		return file.getPath();
	}

	private InputStream getInputStreamFromURL(String url) throws IOException {
		URLConnection conn = new URL(url).openConnection();
		return conn.getInputStream();
	}

	public void transformXML(DOMSource source, StreamResult result) throws Exception {
		TransformerFactory transformerFactory = TransformerFactory.newInstance();
		Transformer transformer = transformerFactory.newTransformer();
		transformer.transform(source, result);
	}

	private Element addElement(Node parent, String name, String value) {
		Element e = this.launch4jDoc.createElement(name);
		if (value != null) {
			e.setTextContent(value);
		}
		parent.appendChild(e);
		return e;
	}

	private void runLaunch4j(File f) throws IOException, Exception {
		String launch4jExe = System.getenv("LAUNCH4J_HOME");
		if (launch4jExe == null) {
			launch4jExe = "c:" + File.separator + "Program Files" + File.separator + "Launch4j/";
		}
		File exeFile = new File(launch4jExe + LAUNCH4J_EXE);
		if (!exeFile.isFile()) {
			exeFile = new File(
					"c:" + File.separator + "Program Files (x86)" + File.separator + "Launch4j/" + LAUNCH4J_EXE);
			if (!exeFile.isFile()) {
				throw new Exception("MISSING_LAUNCH4J");
			}
		}
		String execPath = exeFile.getPath();
		String fPath = f.getPath();
		ExecHelper.exec(new String[] { execPath, fPath });
	}

	private void runNSIS(File dir) throws IOException, Exception {
		String nsisExe = System.getenv("NSIS_HOME");
		if (nsisExe == null) {
			nsisExe = "c:" + File.separator + "Program Files+" + File.separator + "NSIS/";
		}
		File exeFile = new File(nsisExe + NSIS_EXE);
		if (!exeFile.isFile()) {
			exeFile = new File("C:" + File.separator + "Program Files (x86)" + File.separator + "NSIS/" + NSIS_EXE);
			if (!exeFile.isFile()) {
				throw new Exception("MISSING_NSIS");
			}
		}

		String[] args = { exeFile.getPath(), dir.getCanonicalPath() + "" + File.separator + "nms.nsi" };

		Runtime.getRuntime().exec(args);
	}

	private String readFileAsString(String filePath) throws IOException {
		BufferedReader reader = null;
		try {
			StringBuffer fileData = new StringBuffer();
			InputStream is = new FileInputStream(new File(filePath));
			reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
			char[] buf = new char[1024];
			int numRead = 0;
			while ((numRead = reader.read(buf)) != -1) {
				String readData = String.valueOf(buf, 0, numRead);
				fileData.append(readData);
			}
			return fileData.toString();
		} finally {
			if (reader != null)
				reader.close();
		}
	}

	public static void processDirectory(String baseDir) throws IOException {
		File javaLibDir = new File(baseDir, "java/lib");
		File nmsLibDir = new File(baseDir, "nmslib");
		File productDir = new File(baseDir, "java/product");
		File destLogoFile = new File(baseDir, "logo16.ico");
		File destLicenseFile = new File(baseDir, "license.properties");
		File destNSIFile = new File(baseDir, "nms.nsi");

		// Ensure destination directory exists
		if (!nmsLibDir.exists()) {
			boolean created = nmsLibDir.mkdirs();
			if (!created) {
				throw new IOException("Failed to create directory: " + nmsLibDir);
			}
		}

		// Process .jar files in java/lib directory
		if (javaLibDir.exists() && javaLibDir.isDirectory()) {
			File[] jarFiles = javaLibDir.listFiles((dir, name) -> name.endsWith(".jar"));
			if (jarFiles != null) {
				for (File jarFile : jarFiles) {
					File destFile = new File(nmsLibDir, jarFile.getName());
					Files.copy(jarFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
					buildAutomation.appendTextToLog("Copied: " + jarFile.getName() + " to " + nmsLibDir.getPath());
				}
			}
		}

		// Search and copy logo16.ico file
		File logoFile = findFile(productDir, "logo16.ico");
		if (logoFile != null) {
			Files.copy(logoFile.toPath(), destLogoFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
			buildAutomation.appendTextToLog("Copied logo16.ico to " + destLogoFile.getPath());
		} else {
			throw new IOException("logo16.ico not found in any subdirectory of " + productDir.getPath());
		}

		// Search and copy license.properties file
		File licenseFile = findFile(productDir, "license.properties");
		if (licenseFile != null) {
			Files.copy(licenseFile.toPath(), destLicenseFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
			buildAutomation.appendTextToLog("Copied license.properties to " + destLicenseFile.getPath());
		} else {
			throw new IOException("license.properties not found in any subdirectory of " + productDir.getPath());
		}
		// Search and copy nms.nsi file
		File nsiFile = findFile(productDir, "nms.nsi");
		if (nsiFile != null) {
			Files.copy(nsiFile.toPath(), destNSIFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
			buildAutomation.appendTextToLog("Copied nms.nsi to " + destNSIFile.getPath());
		} else {
			throw new IOException("license.properties not found in any subdirectory of " + productDir.getPath());
		}
	}

	private static File findFile(File dir, String fileName) {
		if (dir == null || !dir.isDirectory()) {
			return null;
		}

		File[] files = dir.listFiles();
		if (files != null) {
			for (File file : files) {
				if (file.isDirectory()) {
					File result = findFile(file, fileName);
					if (result != null) {
						return result;
					}
				} else if (file.isFile() && file.getName().equals(fileName)) {
					return file;
				}
			}
		}

		return null;
	}

	private static void copyDirectory(File source, File destination) throws IOException {
		if (!source.exists()) {
			throw new IOException("Source directory does not exist: " + source.getAbsolutePath());
		}

		if (source.isDirectory()) {
			// Ensure destination directory exists
			if (!destination.exists()) {
				if (!destination.mkdirs()) {
					throw new IOException("Failed to create directory: " + destination.getAbsolutePath());
				}
			}

			// List all files and directories in the source directory
			String[] files = source.list();
			if (files != null) {
				for (String file : files) {
					File srcFile = new File(source, file);
					File destFile = new File(destination, file);
					if (srcFile.isDirectory()) {
						copyDirectory(srcFile, destFile); // Recursive call
					} else {
						Files.copy(srcFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
					}
				}
			}
		} else {
			throw new IOException("Source is not a directory: " + source.getAbsolutePath());
		}
	}

	public static String adjustUrl(String urlString) {
		try {
			URL url = new URL(urlString);
			// Construct the base URL up to /nms/
			String adjustedUrl = url.getProtocol() + "://" + url.getHost() + ":" + url.getPort() + "/nms/";
			return adjustedUrl;
		} catch (MalformedURLException e) {
			e.printStackTrace();
			return null;
		}
	}

	public static boolean cleanDirectory(Path dir) throws IOException {
		if (!Files.exists(dir)) {
			buildAutomation.appendTextToLog("Directory does not exist: " + dir);
			return false;
		}
		if (!Files.isDirectory(dir)) {
			buildAutomation.appendTextToLog("Not a directory: " + dir);
			return false;
		}

		Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.delete(file);

				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
				if (exc == null) {
					Files.delete(dir);

					return FileVisitResult.CONTINUE;
				} else {
					throw exc;
				}
			}
		});

		buildAutomation.appendTextToLog("Deleted directory complete");
		return true;
	}
}
