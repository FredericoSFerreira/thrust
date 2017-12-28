package br.com.softbox.thrust.core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.script.Bindings;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import javax.script.SimpleScriptContext;

import br.com.softbox.thrust.util.ThrustUtils;
import jdk.nashorn.api.scripting.*;

public class ThrustCore {
	private static ScriptEngine engine;
	private static ScriptContext rootContext;
	private static Bindings rootScope;
	
	private static String rootPath;
	
	static {
		System.setProperty("nashorn.args", "--language=es6");
		
		engine = new ScriptEngineManager().getEngineByName("nashorn");
		rootContext = engine.getContext();
		rootScope = rootContext.getBindings(ScriptContext.ENGINE_SCOPE);
		rootPath = new File("").getAbsolutePath();
	}
	
	public ThrustCore() throws ScriptException, IOException {
		initialize();
	}
	
	public ThrustCore(String applicationName) throws ScriptException, IOException {
		String thrustDirectory = System.getProperty("thrust.root.path");
		if(thrustDirectory == null || "".equals(thrustDirectory)) {
			throw new IllegalStateException("[ERROR] System property \"thrust.root.path\" not set. Please, define it.");
		}
		
		rootPath = thrustDirectory + File.separator + applicationName;
		validateRootPath();
		initialize();
	}
	
	private void initialize() throws ScriptException, IOException {
		ThrustUtils.loadRequireWrapper(engine, rootContext);
		ThrustUtils.loadConfig(rootPath, engine, rootContext);
		loadGlobalBitCodesByConfig();
	}
	
	public void loadScript(String fileName) throws IOException, ScriptException {
        require(fileName, false);
    }
	
	@SuppressWarnings("restriction")
	private void loadGlobalBitCodesByConfig() throws ScriptException {
		try {
			JSObject config = invokeFunction("getConfig");
			Object bitCodeNamesObject = config.getMember("loadToGlobal");
			
			if(bitCodeNamesObject instanceof jdk.nashorn.internal.runtime.Undefined) {
				return;
			}
			
			List<String> bitCodeNames = new ArrayList<String>();
			
			if(bitCodeNamesObject instanceof String) {
				bitCodeNames.add((String) bitCodeNamesObject);
			} else {
				for(Map.Entry<String, Object> entry : ((ScriptObjectMirror) bitCodeNamesObject).entrySet()) {
					bitCodeNames.add((String) entry.getValue());
				}
			}
			
			for(String bitCodeName : bitCodeNames) {
				bitCodeName = bitCodeName.trim();
				String bitCodeFileName = bitCodeName.startsWith("lib/") ? bitCodeName : "lib/" + bitCodeName;
				bitCodeFileName = bitCodeFileName.endsWith(".js") ? bitCodeFileName : bitCodeFileName + ".js";
				
				int firstIndexToSearch = bitCodeName.lastIndexOf('/') > -1 ? bitCodeName.lastIndexOf('/') : 0;
				bitCodeName = bitCodeName.replaceAll(".js", "").substring(firstIndexToSearch, bitCodeName.length());
				
				engine.eval("var " + bitCodeName + " = require('" + bitCodeFileName + "')", rootContext);
			}
		} catch(NoSuchMethodException e) {
			e.printStackTrace();
		}
	}
	
	@SuppressWarnings("restriction")
	public JSObject eval(String expression) throws ScriptException {
		Bindings reqScope = new SimpleBindings();
		reqScope.putAll(engine.getContext().getBindings(ScriptContext.ENGINE_SCOPE));
		
		ScriptContext reqContext = new SimpleScriptContext();
		reqContext.setBindings(reqScope, ScriptContext.ENGINE_SCOPE);
		
		JSObject result = null;
		try {
			result = (JSObject) engine.eval(expression, reqContext);
		} catch(ClassCastException ignored) { }
		
		return result;
	}
	
	@SuppressWarnings("restriction")
	public JSObject invokeFunction(String function, Object... params) throws NoSuchMethodException, ScriptException {
		Invocable inv = (Invocable) engine;
		String[] fullPath = function.split("\\.");
		
		if(fullPath.length == 1) {
			return (JSObject) inv.invokeFunction(function, params);
		}
		
		ScriptObjectMirror scriptObjectMirror = (ScriptObjectMirror) rootScope.get(fullPath[0]);
		int i = 1;
		for(;i < (fullPath.length - 1); i++) {
			scriptObjectMirror = (ScriptObjectMirror) scriptObjectMirror.get(fullPath[i]);
		}
		
		JSObject result = null;
		try {
			result = (JSObject) scriptObjectMirror.callMember(fullPath[i], params);
		} catch(ClassCastException ignored) { }
		
		return result;
	}
	
	@SuppressWarnings("restriction")
	public static ScriptObjectMirror require(String fileName, boolean loadToGlobal) {
		ScriptObjectMirror scriptObject = null;
		
		try {
			String fileNameNormalized = fileName.endsWith(".js") ? fileName : fileName.concat(".js");
			File scriptFile = new File(rootPath + File.separator + fileNameNormalized);
			
			String scriptContent = new String(Files.readAllBytes(scriptFile.toPath()), StandardCharsets.UTF_8);
			
			if(loadToGlobal) {
				scriptObject = (ScriptObjectMirror) engine.eval(scriptContent, rootContext);
			} else {
				Bindings reqScope = new SimpleBindings();
				reqScope.putAll(engine.getContext().getBindings(ScriptContext.ENGINE_SCOPE));
				
				ScriptContext reqContext = new SimpleScriptContext();
				reqContext.setBindings(reqScope, ScriptContext.ENGINE_SCOPE);
				
				setupContext(reqContext);
				
				scriptObject = (ScriptObjectMirror) engine.eval(scriptContent, reqContext);
			}
		} catch(IOException e) {
			System.out.println("[ERROR] Cannot load " + fileName + " module.");
			e.printStackTrace();
		} catch(ScriptException se) {
			System.out.println("[ERROR] Error running module code: " + fileName + ".");
			se.printStackTrace();
		}
		
		return scriptObject;
	}
	
	private static void validateRootPath() {
		File file = new File(rootPath);
		if(!file.exists()) {
			throw new IllegalStateException("[ERROR] Invalid rootPath: \"" + rootPath + "\".");
		}
	}

	private static void setupContext(ScriptContext context) throws ScriptException {
		ThrustUtils.loadPolyfills(engine, context);
	}
}