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
import com.nms.support.nms_support.service.globalPack.LoggerUtil;
import com.nms.support.nms_support.service.globalPack.ManageFile;
import com.nms.support.nms_support.service.globalPack.ProgressCallback;
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

	private ProgressCallback progressCallback;

	public boolean execute(String appURL, String envVarName, ProjectEntity project, ProgressCallback progressCallback) throws Exception {

		this.progressCallback = progressCallback;

		progressCallback.onProgress(5, "Starting installer creation process...");

		dir_temp = project.getExePath(); // where all the installer and extracted java folder is placed
		installer_loc = project.getExePath();
		File folder = new File(installer_loc);
		if (!folder.exists()) {
			boolean created = folder.mkdirs(); // Creates directory including any necessary but nonexistent parent directories
			if (created) {
				System.out.println("Folder created: " + installer_loc);
			} else {
				System.out.println("Failed to create folder: " + installer_loc);
			}
		} else {
			System.out.println("Folder already exists: " + installer_loc);
		}
		
		// Create required directories if they don't exist
		File nmsLibDir = new File(dir_temp, "nmslib");
		File javaLibDir = new File(dir_temp, "java/lib");
		if (!nmsLibDir.exists()) {
			nmsLibDir.mkdirs();
		}
		if (!javaLibDir.exists()) {
			javaLibDir.mkdirs();
		}
		
		// Copy hotreload-agent.jar to multiple locations for different purposes
		try {
			// Copy to exe path folder (for -javaagent option at runtime)
			InputStream agentJarStream1 = getClass().getClassLoader().getResourceAsStream("nms_configs/hotreload-agent.jar");
			if (agentJarStream1 != null) {
				File destAgentJar = new File(dir_temp, "hotreload-agent.jar");
				Files.copy(agentJarStream1, destAgentJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
				progressCallback.onProgress(5, "Copied hotreload-agent.jar to " + dir_temp);
				System.out.println("Copied hotreload-agent.jar to " + dir_temp);
				agentJarStream1.close();
			} else {
				System.out.println("Warning: hotreload-agent.jar not found in resources");
			}
			
			// Copy to nmslib folder (for runtime classpath)
			InputStream agentJarStream2 = getClass().getClassLoader().getResourceAsStream("nms_configs/hotreload-agent.jar");
			if (agentJarStream2 != null) {
				File destAgentJarLib = new File(nmsLibDir, "hotreload-agent.jar");
				Files.copy(agentJarStream2, destAgentJarLib.toPath(), StandardCopyOption.REPLACE_EXISTING);
				progressCallback.onProgress(5, "Copied hotreload-agent.jar to nmslib");
				System.out.println("Copied hotreload-agent.jar to nmslib");
				agentJarStream2.close();
			}
			
			// Copy to java/lib folder (for compilation classpath)
			InputStream agentJarStream3 = getClass().getClassLoader().getResourceAsStream("nms_configs/hotreload-agent.jar");
			if (agentJarStream3 != null) {
				File destAgentJarJavaLib = new File(javaLibDir, "hotreload-agent.jar");
				Files.copy(agentJarStream3, destAgentJarJavaLib.toPath(), StandardCopyOption.REPLACE_EXISTING);
				progressCallback.onProgress(5, "Copied hotreload-agent.jar to java/lib for compilation");
				System.out.println("Copied hotreload-agent.jar to java/lib for compilation");
				agentJarStream3.close();
			}
		} catch (IOException e) {
			System.out.println("Warning: Could not copy hotreload-agent.jar: " + e.getMessage());
			// Don't fail the process if agent jar copy fails
		}
		String serverURL = adjustUrl(appURL);

		
		// try {
		// 	processDirectory(dir_temp);
		// } catch (IOException e) {
		// 	e.printStackTrace();
		// 	LoggerUtil.error(e);
		// 	buildAutomation.appendTextToLog(e.getMessage());
		// 	buildAutomation.appendTextToLog("process dir failed");
		// 	return false;
		// }

		String currentDir = System.getProperty("user.dir");

		// Print the current working directory
		progressCallback.onProgress(5, "Current Directory: " + currentDir);
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

		progressCallback.onProgress(10, "Working directory: " + dir.toString());

		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setAttribute(XMLConstants.FEATURE_SECURE_PROCESSING, Boolean.TRUE);
		DocumentBuilder docBuilder = factory.newDocumentBuilder();

		Properties props = new Properties();
		try (InputStream propsIS = new FileInputStream(dir_temp + "/license.properties")) { // Update with actual path
			props.load(propsIS);
		} catch (Exception e) {
			progressCallback.onError("Failed to load license.properties: " + e.getMessage());
			return false;
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
				//buildAutomation.appendTextToLog("JRE directory copied successfully.");
			} catch (IOException e) {
				e.printStackTrace();
				LoggerUtil.error(e);
				//buildAutomation.appendTextToLog("jre files copy process failed");
				return false;
			}
		}
		String failed_setups="";
		int success_count = 0;
		int totalProducts = products.length;
		int processedProducts = 0;
		
		progressCallback.onProgress(15, "Processing " + totalProducts + " products...");
		
		for (String product : products) {
			processedProducts++;
			int progress = 15 + (int)((processedProducts * 60.0) / totalProducts); // 15-75% range
			progressCallback.onProgress(progress, "Processing product " + processedProducts + "/" + totalProducts + ": " + product);
			this.launch4jDoc = docBuilder.newDocument();
			Element launch4j = addElement(this.launch4jDoc, "launch4jConfig", (String) null);

			String name = props.getProperty(product);
			String jnlpName = props.getProperty(product + ".jnlpName", product);
			String iUrl = serverURL + jnlpName + ".jnlp";
			String saveClasspath = null;

			try {
				InputStream is = null;
				// Try to load .jnlp file first
				try {
					is = getInputStreamFromURL(iUrl);
				} catch (Exception e) {
					// If .jnlp fails, try .jnlpx as fallback
					try {
						String iUrlFallback = serverURL + jnlpName + ".jnlpx";
						is = getInputStreamFromURL(iUrlFallback);
						LoggerUtil.getLogger().severe("Failed to load .jnlp file, using .jnlpx fallback: " + iUrlFallback);
					} catch (Exception fallbackException) {
						// Both failed, handle per-product failure and continue
						failed_setups += product + ", ";
						LoggerUtil.getLogger().severe("Failed to load both .jnlp and .jnlpx files for " + jnlpName);
						LoggerUtil.error(e);
						progressCallback.onProgress(progress, "Failed to process " + product + ": cannot load JNLP/JNLPX");
						continue;
					}
				}

				try (InputStream inputStream = is) {
				str.append(TEMPLATE.replace("%long%", name).replace("%short%", jnlpName));
				Document document = docBuilder.parse(inputStream);

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
						} else if (value.equals("--add-opens")) {
    						systemProps.add(value + "=" + setting);
						}
						else if (value.startsWith("-")) {
							systemProps.add(value);
							i--;
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
				systemProps.add("-DPROJECT_NAME=" + project.getName());
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
//				buildAutomation.appendTextToLog(list.toString());
				(new File(dir, "images")).mkdir();
				for (int j = 0; j < list.getLength(); j++) {
					Element elem = (Element) list.item(j);
					String href = elem.getAttribute("href");
//					buildAutomation.appendTextToLog(" href - " + href);
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
				if(saveClasspath != null) {
					for (String jar : saveClasspath.split(";")) {
						cpList.add("nmslib/" + jar);
					}
				}
				for (String cp : cpList) {
					addElement(classPath, "cp", cp);
				}

				addElement(classPath, "cp", "nmslib/wlthint3client.jar");
				// Add hotreload-agent.jar to classpath for HotReloadAgent class availability
				addElement(classPath, "cp", "nmslib/hotreload-agent.jar");
				Element app = (Element) document.getElementsByTagName("application-desc").item(0);
				addElement(classPath, "mainClass", "com.splwg.oms.fcp.JWSLauncher");
				addElement(jre, "minVersion", "1.8.0");
				// Add hotreload agent with search path configuration
				addElement(jre, "opt", "-javaagent:hotreload-agent.jar");
				// Tell the agent where to search for class files (Oracle NMS uses java/working/config)
				addElement(jre, "opt", "-Dhotreload.search.path=java/working/config");
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
				// Count success if outfile exists
				File expectedExe = new File(dir, jnlpName + ".exe");
				if (expectedExe.exists()) {
					success_count++;
				}
				launchXML.delete();
			}
			} catch (Exception e) {
				failed_setups += product + ", ";
				LoggerUtil.error(e);
				progressCallback.onProgress(progress, "Failed to process " + product + ": " + e.getMessage());
				continue;
			}
		}
		String config = readFileAsString(dir_temp + "/nms.nsi");
		String appName = "Oracle Utilities NMS";

		config = config.replace("%INSTALLER_LOC%", installerLocation.getAbsolutePath()).replace("%name%", appName)
				.replace(";### COMPONENTS ###", str.toString());

		progressCallback.onProgress(80, "Creating NSIS configuration file...");
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

		//progressCallback.onProgress(85, "Running NSIS installer creation...");
		//runNSIS(dir);

		if (success_count == 0) {
			progressCallback.onError("Installer creation failed: no executables were created.");
			return false;
		}
		if (failed_setups != null && !failed_setups.trim().isEmpty()) {
			progressCallback.onProgress(100, "Some products failed: " + failed_setups);
		}
		progressCallback.onComplete("Installer creation completed. EXEs created: " + success_count + ".");
		return true;
	}

	private boolean createUserEnvVar(String variableName, String variableValue) {
		try {
			// Quote the value to support spaces and special characters
			String command = String.format("setx %s \"%s\"", variableName, variableValue);

			// Run using cmd
			ProcessBuilder processBuilder = new ProcessBuilder("cmd.exe", "/c", command);
			Process process = processBuilder.start();

			// Print output (for debugging)
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
				 BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {

				String line;
				while ((line = reader.readLine()) != null) {
					System.out.println("OUTPUT: " + line);
				}

				while ((line = errReader.readLine()) != null) {
					System.err.println("ERROR: " + line);
				}
			}

			int exitCode = process.waitFor();
			if (exitCode == 0) {
				System.out.println("User environment variable set successfully.");
				return true;
			} else {
				System.err.println("Failed to set environment variable. Exit code: " + exitCode);
				return false;
			}
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
			LoggerUtil.error(e);
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
			throw new Exception("JDK_NOT_FOUND (WITH JRE)");
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
		// Resolve Launch4j executable robustly: LAUNCH4J_HOME, standard paths, then PATH
		File exeFile = null;

		// 1) Try LAUNCH4J_HOME (proper join to avoid missing separators)
		String launch4jHome = System.getenv("LAUNCH4J_HOME");
		if (launch4jHome != null && !launch4jHome.trim().isEmpty()) {
			File homeDir = new File(launch4jHome.trim());
			File candidate = new File(homeDir, LAUNCH4J_EXE);
			if (candidate.isFile()) {
				exeFile = candidate;
			}
		}

		// 2) Try standard installation locations
		if (exeFile == null) {
			File candidate = new File("C:" + File.separator + "Program Files" + File.separator + "Launch4j", LAUNCH4J_EXE);
			if (candidate.isFile()) {
				exeFile = candidate;
			}
		}
		if (exeFile == null) {
			File candidate = new File("C:" + File.separator + "Program Files (x86)" + File.separator + "Launch4j", LAUNCH4J_EXE);
			if (candidate.isFile()) {
				exeFile = candidate;
			}
		}

		// 3) Search PATH for any directory containing "launch4j" with the exe present
		if (exeFile == null) {
			String pathEnv = System.getenv("PATH");
			if (pathEnv != null && !pathEnv.trim().isEmpty()) {
				String[] pathDirs = pathEnv.split(";");
				for (String pathDir : pathDirs) {
					if (pathDir != null && pathDir.toLowerCase().contains("launch4j")) {
						File pathDirFile = new File(pathDir.trim());
						File candidate = new File(pathDirFile, LAUNCH4J_EXE);
						if (candidate.isFile()) {
							exeFile = candidate;
							break;
						}
					}
				}
			}
		}

		if (exeFile == null || !exeFile.isFile()) {
			throw new Exception("MISSING_LAUNCH4J");
		}

		String execPath = exeFile.getPath();
		String fPath = f.getPath();
		ExecHelper.exec(new String[] { execPath, fPath });
	}

	private void runNSIS(File dir) throws IOException, Exception {
		//This method required attach.dll to be copied
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

	public  void processDirectory(String baseDir) throws IOException {
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
					progressCallback.onProgress(0, "Copied: " + jarFile.getName() + " to " + nmsLibDir.getPath());
				}
			}
		}

		// Search and copy logo16.ico file
		File logoFile = findFile(productDir, "logo16.ico");
		if (logoFile != null) {
			Files.copy(logoFile.toPath(), destLogoFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
			progressCallback.onProgress(0, "Copied logo16.ico to " + destLogoFile.getPath());
		} else {
			throw new IOException("logo16.ico not found in any subdirectory of " + productDir.getPath());
		}

		// Search and copy license.properties file
		File licenseFile = findFile(productDir, "license.properties");
		if (licenseFile != null) {
			Files.copy(licenseFile.toPath(), destLicenseFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
			progressCallback.onProgress(0, "Copied license.properties to " + destLicenseFile.getPath());
		} else {
			throw new IOException("license.properties not found in any subdirectory of " + productDir.getPath());
		}
		// Search and copy nms.nsi file
		File nsiFile = findFile(productDir, "nms.nsi");
		if (nsiFile != null) {
			Files.copy(nsiFile.toPath(), destNSIFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
			progressCallback.onProgress(0, "Copied nms.nsi to " + destNSIFile.getPath());
		} else {
			throw new IOException("nms.nsi not found in any subdirectory of " + productDir.getPath());
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

	private  void copyDirectory(File source, File destination) throws IOException {
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

	public  String adjustUrl(String urlString) {
		try {
			URL url = new URL(urlString);
			// Construct the base URL up to /nms/
			String adjustedUrl = url.getProtocol() + "://" + url.getHost() + ":" + url.getPort() + "/nms/";
			return adjustedUrl;
		} catch (MalformedURLException e) {
			e.printStackTrace();
			LoggerUtil.error(e);
			return null;
		}
	}

	public  boolean cleanDirectory(Path dir) throws IOException {
		if (!Files.exists(dir)) {
			progressCallback.onProgress(0, "Directory does not exist: " + dir);
			return false;
		}
		if (!Files.isDirectory(dir)) {
			progressCallback.onProgress(0, "Not a directory: " + dir);
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

		progressCallback.onProgress(0, "Deleted directory complete");
		return true;
	}
}
