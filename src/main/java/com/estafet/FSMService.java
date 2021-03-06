package com.estafet;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.StringTokenizer;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.json.JSONObject;

import com.estafet.scribble.easyfsm.FSM.FSM;

@Path("/fsmserver")
public class FSMService {
	
	private final static String SCRIBBLEDIR = "/opt/app-root/src/src/main/resources"; 
	private final static String LOCATION = SCRIBBLEDIR;
	private final static String URL = "/fsmserver/api";
	private final static String SCRIBBLE_DELIM = "____";

	private final Shell shell = new Shell();
	
	private FSM fsm;
	private String myrole = "generic";
	private String payload = URL + " response.";

	@GET
	@Path("/api")
	public String sayHello() {
		java.nio.file.Path currentRelativePath = Paths.get("");
		String currentPath = currentRelativePath.toAbsolutePath().toString();
		
		String result;
		try {
			result = shell.executeCommand("sh ./src/main/resources/bin/abc.sh");
			result = getAsHTMLTable(result);
		} catch (ShellException se) {
			result = "<b><font color='red'>Error has occurred</font></b><br/>" + se.getMessage();
		}
		
		StringBuffer stringBuffer = new StringBuffer();
		stringBuffer.append("<h1>FSM Service'ds project</h1>");
		stringBuffer.append("<br/>");
		stringBuffer.append("<b>Your current dir is :</b>");
		stringBuffer.append("<i>" + currentPath + "</i>");
		stringBuffer.append("<br/>");
		stringBuffer.append(result);

		return stringBuffer.toString();
	}

	private String getAsHTMLTable(String result) {
		StringBuffer stringBuffer = new StringBuffer();
		if (result != null) {
			String[] lines = result.split("\n");
			if (lines.length >= 1) {
				stringBuffer.append(lines[0]);
			}
			stringBuffer.append("<table border='0'>");
			for(int i = 1; i < lines.length; i++) {
				stringBuffer.append("<tr>");
				String[] columns = lines[i].split(" ");
				for (int j = 0; j < columns.length; j++) {
					if (columns[j].trim().length() > 0) {
						stringBuffer.append("<td>");
						stringBuffer.append(columns[j].trim());
						stringBuffer.append("</td>");
					}
				}
				stringBuffer.append("</tr>");
			}
			stringBuffer.append("</table>");
		}
		return stringBuffer.toString();
	}

	@POST
	@Path("/api/{command}")
	public Response postString(@PathParam("command") final String command, String body) {
		System.out.println(command);
		System.out.println(body);
		
		try {
			String result = fsmPost(command, body);
			return Response.ok(result, MediaType.TEXT_PLAIN).build();
		} catch (FSMException e) {
			return Response.serverError().entity(e.getMessage()).type(MediaType.TEXT_PLAIN).build();
		}
	}

	private String fsmPost(String command, String body) throws FSMException {
		System.out.println("\n------- GOT REQUEST METHOD: " +  "-------");
		System.out.println("URI is <" + command + ">");
		
		String[] p = extractParametersFrom(command, SCRIBBLE_DELIM);
		String event = getParameter(p, 0);
		System.out.println("Event: " + event);

		if (body != null) {
			body = body.replaceAll("xxxx", "\n");
		}

		if (event.startsWith("eppLoad")) {
			String protocolName = getParameter(p, 1);
			String roleName = getParameter(p, 2);
			String startState = null;
			myrole = roleName;
			
			// Extract the file name and load the behavior into the FSM
			System.out.println("Loading easyFSM role from scribble");
			
			String logMessage = "Instantiating FSM for role '" + roleName + "' based on '" + protocolName + "'";
			if (p.length >= 4) {
				startState = getParameter(p, 3);
				logMessage += " starting at '" + startState + "'";
			}
			System.out.println(logMessage);

			eppLoad(body, protocolName, roleName);
			fsm = eppInstantiate(roleName);
			String currentState = fsm.getCurrentState();
			System.out.println("FSM current state " + currentState);
			String nextStates[] = null;
			nextStates = fsm.getValidCommands();
			String availableToDo = "";
			for (int i = 0; (i < nextStates.length); i++) {
				availableToDo = availableToDo + "    <" + nextStates[i] + ">\n";
			}
			System.out.println("Accepting:\n" + availableToDo);
			payload = "Instantiated FSM for role '" + roleName + "' based on '" + protocolName + "' for " + URL + "\nAccepting:\n" + availableToDo;
			// Should be two/three parameters, one is the scribble and the other
			// is the role name to play
			// and the third (optional) is the starting state
		} else {
			// Handle the event as an FSM event
			System.out.println(
					"Trying to execute as an FSM in the role of " + myrole + " with the message <" + command + ">");
			String message = command;
			String m = message.substring(message.indexOf(SCRIBBLE_DELIM) + SCRIBBLE_DELIM.length());
			payload = body == null ? FSMExecute(fsm, m) : FSMExecute(fsm, m, body);
		}

		return payload;
	}
	
	private String getParameter(String[] parameters, int index) {
		if (index < parameters.length) {
			return parameters[index].substring(parameters[index].indexOf("=") + 1);
		}
		return "";
	}

	private String eppLoad(String scribble, String protocol, String role) {
		System.out.println("eppLoad ...");
		// System.out.println("<<<<\n" + scribble + "\n<<<<<");
		// 1. Save scribble to a file.
		// 2. build command
		// ${ROOT}/bin/eppLoad scribble(as a file) protocol role
		// 3. exec the command
		// 4. find and open the resultant <role>_config.txt in ${ROOT}/generated
		String[] lines = scribble.split(";");
		String moduleName = "";
		// System.out.println(lines.length + " number of lines in scribble");
		for (int i = 0; (i < lines.length); i++) {
			// System.out.println("lines[" + i + "]<" + lines[i] + ">");
			if (lines[i].contains("module")) {
				moduleName = lines[i].substring(lines[i].lastIndexOf(".") + 1);
				break;
			}
		}
		String scribbleFile = LOCATION + "/bin/generated/" + moduleName + ".scr";
		// Save the scribble to icribb eFile
		PrintWriter out = null;
		try {
			out = new PrintWriter(new FileWriter(scribbleFile));
			out.println(scribble);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (out != null) {
				out.close();
			}
		}
	
		// Construct epp command
		String command = "sh " + LOCATION + "/bin/epp.sh " + scribbleFile + " " + protocol + " " + role;
		System.out.println("command <" + command + ">");

		String output;
		try {
			output = shell.executeCommand(command);
		} catch (ShellException se) {
			output = se.getMessage();
		}

		System.out.println("***\n" + output + "\n***");
		
		try {
			output = shell.executeCommand("ls -ls " + LOCATION + "/bin/generated");
		} catch (ShellException se) {
			output = se.getMessage();
		}
		System.out.println("***\n" + output + "\n***");

		return LOCATION + "/bin/generated" + role + "_config.txt";
	}

	private String[] extractParametersFrom(String uri, String delim) {
		String[] parameters = null;
		StringTokenizer st = new StringTokenizer(uri, delim);
		parameters = new String[st.countTokens()];

		int i = 0;
		while (st.hasMoreTokens()) {
			parameters[i++] = st.nextToken();
		}
		return parameters;
	}

	private String FSMExecute(FSM f, String msg, String data) throws FSMException {
		String availableToDo = "";
		String tmpmsg = msg;

		System.out.println("FSMExecute(" + f + "," + tmpmsg + "," + data + ")");
		try {
			JSONObject object = new JSONObject(data);
			System.out.println("data is a JSON object <" + object + ">");
			//
			// Build FSM message from tmpmsg and data
			//
			tmpmsg = tmpmsg + "(" + object.getString("function") + "(____))__";
			try {
				tmpmsg = tmpmsg + "from_" + object.getString("from");
			} catch (org.json.JSONException e1) {
				try {
					tmpmsg = tmpmsg + "to_" + object.getString("to");
				} catch (org.json.JSONException e2) {

				}
			}

			String tmp = "";
			try {
				JSONObject params = object.getJSONObject("parameters");

				// System.out.println("params are: <" + params + ">");
				for (int i = 1; (i <= params.length()); i++) {
					JSONObject o = params.getJSONObject("param" + i);
					tmp = tmp + o.names().get(0) + ", ";
				}
				tmp = tmp.substring(0, tmp.length() - 2);
			} catch (org.json.JSONException e3) {
				// System.out.println("no parameters");
			}
			tmpmsg = tmpmsg.replaceAll(SCRIBBLE_DELIM, tmp);
			System.out.println("xsposed msg is <" + tmpmsg + ">");

			String currentState = f.getCurrentState();
			System.out.println("Current state before execution is: <" + currentState + ">");
			String nextStates[] = null;
			nextStates = f.getValidCommands();
			System.out.println("Valid next states before execution are:");
			for (int i = 0; (i < nextStates.length); i++) {
				System.out.println("    nextstate[" + i + "]: <" + nextStates[i] + ">");
				availableToDo = availableToDo + "    <" + nextStates[i] + ">\n";
			}
			if (f.ProcessFSM(tmpmsg) == null) {
				System.out.println("*** ERROR ***");
				System.out.println("    " + tmpmsg + " has no matching state transition");
				return "*** ERROR ***\n" + tmpmsg + " has no matching state transition. Current state is "
						+ currentState + ".\n" + availableToDo;
			}
			currentState = f.getCurrentState();
			System.out.println("Current state after execution is: <" + currentState + ">");
			nextStates = f.getValidCommands();
			availableToDo = "";
			System.out.println("Valid next states after execution are:");

			for (int i = 0; (i < nextStates.length); i++) {
				System.out.println("    nextstate[" + i + "]: <" + nextStates[i] + ">");
				availableToDo = availableToDo + "    <" + nextStates[i] + ">\n";
			}
			return "Valid next states from " + currentState + " are:\n" + availableToDo;
		} catch (Exception e) {
			throw new FSMException(e.getMessage());
		}
	}

	private String FSMExecute(FSM f, String msg) throws FSMException {
		String availableToDo = "";
		String tmpmsg = msg;

		System.out.println("FSMExecute(" + f + "," + tmpmsg + ")");
		try {
			String currentState = f.getCurrentState();
			System.out.println("Current state before execution is: <" + currentState + ">");
			String nextStates[] = null;
			nextStates = f.getValidCommands();
			System.out.println("Valid next states before execution are:");
			for (int i = 0; (i < nextStates.length); i++) {
				System.out.println("    nextstate[" + i + "]: <" + nextStates[i] + ">");
				availableToDo = availableToDo + "    <" + nextStates[i] + ">\n";
			}
			if (f.ProcessFSM(tmpmsg) == null) {
				System.out.println("*** ERROR ***");
				System.out.println("    " + tmpmsg + " has no matching state transition");
				return "*** ERROR ***\n" + tmpmsg + " has no matching state transition. Current state is "
						+ currentState + ".\n" + availableToDo;
			}
			currentState = f.getCurrentState();
			System.out.println("Current state after execution is: <" + currentState + ">");
			nextStates = f.getValidCommands();
			availableToDo = "";
			System.out.println("Valid next states after execution are:");
			for (int i = 0; (i < nextStates.length); i++) {
				System.out.println("    nextstate[" + i + "]: <" + nextStates[i] + ">");
				availableToDo = availableToDo + "    <" + nextStates[i] + ">\n";
			}
			return "Valid next states from " + currentState + " are:\n" + availableToDo;
		} catch (Exception e) {
			throw new FSMException(e.getMessage());
		}
	}

	private FSM eppInstantiate(String roleName) throws FSMException {
		String config = LOCATION + "/bin/generated/" + roleName + "_config.txt";
		// 1. Open file <role>_config.txt
		// 2. conduct start up of FSM
		// 3. run FSM in modified runloop of httpserver
		try {
			FileInputStream inputStream = new FileInputStream(config);
			if (inputStream != null)
				System.out.println("Got file " + config);
			FSM fsm = new FSM(inputStream, null);
			String currentState = fsm.getCurrentState();
			System.out.println("FSM Instantiating for role " + roleName + ": current state is: <" + currentState + ">");
			String nextStates[] = fsm.getValidCommands();
			for (int i = 0; (i < nextStates.length); i++) {
				System.out.println("nextstate[" + i + "]: <" + nextStates[i] + ">");
			}
			return fsm;
		} catch (Exception e) {
			throw new FSMException(e.getMessage());
		}
	}
}
