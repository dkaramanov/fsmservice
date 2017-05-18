package com.estafet;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class Shell {

	public static void main(String[] args) {
		Shell obj = new Shell();

		String domainName = "google.com";

		//in mac oxs
		String command = "ping -c 3 " + domainName;

		//in windows
		//String command = "ping -n 3 " + domainName;

		try {
			String output = obj.executeCommand(command);
			System.out.println(output);
		} catch (ShellException se) {
			System.out.println(se.getMessage());
		}
	}

	public String executeCommand(String command) throws ShellException {
		StringBuffer output = new StringBuffer();

		try {
			Process process = Runtime.getRuntime().exec(command);
			process.waitFor();
			BufferedReader reader =
                            new BufferedReader(new InputStreamReader(process.getInputStream()));

			String line;
			while ((line = reader.readLine())!= null) {
				output.append(line + "\n");
			}
			
		} catch (Exception e) {
			e.printStackTrace();
			throw new ShellException(e.getMessage());
		}

		return output.toString();
	}
}
