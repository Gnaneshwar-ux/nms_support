package custom;

import java.util.Properties;
import java.io.FileWriter;
import java.io.*;
import java.util.*;
import javax.swing.*;
import com.splwg.oms.jbot.JBotCommand;
import com.splwg.oms.jbot.component.*;
import com.splwg.oms.jbot.IDataStore;
import com.splwg.oms.jbot.IDataRow;
import com.splwg.oms.jbot.DefaultDataStore;
import com.splwg.oms.jbot.AbstractDataStore;
import com.splwg.oms.jbot.JBotObjectNotFoundException;
import javax.swing.JOptionPane;
import com.splwg.oms.client.login.LoginHelper;


public class LoadCredentialsExternalCommand extends JBotCommand{
	public void execute() throws Exception{

		//String system = (String) this.dataManager.getValue("system");
		String systemCode = (String) LoginHelper.getSystem();
		String projectName = (String) System.getProperty("PROJECT_NAME");
		//String systemName = (String) this.dataManager.getValue("system_name");

		//System.out.println("System: " + system);
		System.out.println("System Code: " + systemCode);
		System.out.println("Project Name: "+ projectName);
		//System.out.println("System Name: " + systemName);
		
		IDataStore cred = (DefaultDataStore)getDataStore("DS_LOGIN_ENTRY");
		
		String user = System.getProperty("user.name");
		
		String propPath = "C:/Users/" + user + "/Documents/nms_support_data";

		try{
			FileReader file = new FileReader(propPath+"/cred.properties");
			Properties p = new Properties();
			p.load(file);

			String proj = projectName+"_"+systemCode+"_";
			
			if(p.getProperty(proj+"autoLogin").toLowerCase().equals("true"))
			{
			cred.setValue("USER",p.getProperty(proj+"username"));
			cred.setValue("PASSWORD", p.getProperty(proj + "password"));
			cred.setValue("TYPE", p.getProperty(proj + "selectedUserType"));
			cred.setValue("autoLogin",true);
			}
			else {
				cred.setValue("autoLogin", false);
			}

			file.close();
			System.out.println("\nUSERNAME = "+ user+"\n");
			System.out.println("Credentials loaded .................");
		}
		catch (Exception e) {
			System.out.println("Exception raised in LoadCredentials command");
		}
	}
}