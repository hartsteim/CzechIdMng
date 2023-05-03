package eu.bcvsolutions.idm.core.model.service.impl;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;
import org.codehaus.groovy.syntax.SyntaxException;
import org.kohsuke.groovy.sandbox.GroovyInterceptor;
import org.kohsuke.groovy.sandbox.SandboxTransformer;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import com.google.common.collect.ImmutableMap;

import eu.bcvsolutions.idm.core.CoreModuleDescriptor;
import eu.bcvsolutions.idm.core.api.config.cache.domain.ValueWrapper;
import eu.bcvsolutions.idm.core.api.domain.CoreResultCode;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.service.GroovyScriptService;
import eu.bcvsolutions.idm.core.api.service.IdmCacheManager;
import eu.bcvsolutions.idm.core.security.domain.GroovySandboxFilter;
import eu.bcvsolutions.idm.core.security.exception.IdmSecurityException;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;

/**
 * Service for evaluate groovy scripts
 * 
 * @author svandav
 *
 */
@Service
public class DefaultGroovyScriptService implements GroovyScriptService {


	public static final String CACHE_NAME = IdmCacheManager.getCacheName(CoreModuleDescriptor.MODULE_ID, "default-groovy-script-service-script-cache");

	@Autowired
	IdmCacheManager cacheManager;

	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory
			.getLogger(DefaultGroovyScriptService.class);
	
	@Override
	public Object evaluate(String script, Map<String, Object> variables) {
		return evaluate(script, variables, null);
	}
	
	@Override
	public Object evaluate(String script, Map<String, Object> variables, List<Class<?>> extraAllowedClasses) {
		Assert.notNull(script, "Script is required.");
		
		Binding binding = new Binding(variables);
		
		Set<Class<?>> allowedVariableClass = resolveCustomAllowTypes(variables);
		if(extraAllowedClasses != null){
			allowedVariableClass.addAll(extraAllowedClasses);
		}
		GroovySandboxFilter sandboxFilter = null;
		//
		try {
			// if groovy filter exist add extraAllowedClasses, into this filter, otherwise create new
			if (!GroovyInterceptor.getApplicableInterceptors().isEmpty()) {
				// exists only one goovy filter
				sandboxFilter = (GroovySandboxFilter) GroovyInterceptor.getApplicableInterceptors().get(0);
				sandboxFilter.addCustomTypes(allowedVariableClass);
			} else {
				sandboxFilter = new GroovySandboxFilter(allowedVariableClass);
				sandboxFilter.register();
			}
			
			// Get script and fill it with variables
			Script scriptObj = getScript(script);
			
			// Scripts aren't thread safe
			synchronized(scriptObj) {
				scriptObj.setBinding(binding);
				return scriptObj.run();
			}
			
		} catch (SecurityException | IdmSecurityException ex) {
			LOG.error("SecurityException [{}]", ex.getLocalizedMessage());
			if (ex instanceof IdmSecurityException) {
				throw ex;
			}
			throw new IdmSecurityException(CoreResultCode.GROOVY_SCRIPT_SECURITY_VALIDATION, ImmutableMap.of("message", ex.getLocalizedMessage()), ex);
		} catch (Exception e) {
			LOG.error("Exception [{}]", e.getLocalizedMessage());
			if (e instanceof ResultCodeException) {
				throw e;
			}
			throw new ResultCodeException(CoreResultCode.GROOVY_SCRIPT_EXCEPTION, ImmutableMap.of("message", e.getLocalizedMessage() != null ? e.getLocalizedMessage() : e.toString()), e);
		} finally {
			// if this script is called from another script, remove only allowed classes from them
			// otherwise unregister all filter.
			if (sandboxFilter != null) {
				if (sandboxFilter.isCustomTypesLast()) {
					sandboxFilter.unregister();
				} else {
					sandboxFilter.removeLastCustomTypes();
				}
			}
		}
	}

	/**
	 * Return all unique class from variables. If is variable list, then add all
	 * classes for all items.
	 * 
	 * @param variables
	 * @return
	 */
	private Set<Class<?>> resolveCustomAllowTypes(Map<String, Object> variables) {
		Set<Class<?>> allowType = new HashSet<>();
		if (variables == null) {
			return allowType;
		}
		variables.forEach((key, object) -> {
			Class<?> targetClass = null;
			if (object != null) {
				targetClass = AopUtils.getTargetClass(object);
			}
			if (targetClass != null && !allowType.contains(targetClass)) {
				allowType.add(targetClass);
			}
			// We have to add types for all list items
			if (object instanceof List) {
				((List<?>) object).stream().forEach(item -> {
					if (item != null && !allowType.contains(AopUtils.getTargetClass(item))) {
						allowType.add(AopUtils.getTargetClass(item));
					}
				});
			}
		});
		return allowType;
	}

	@Override
	public Object validateScript(String script) throws ResultCodeException {
		Assert.notNull(script, "Script is required.");
		try {
			GroovyShell shell = new GroovyShell();
			return shell.parse(script);
		} catch (MultipleCompilationErrorsException e) {
			// get last error, it is possible add all errors
			Object error = e.getErrorCollector().getLastError();
			if (error instanceof SyntaxErrorMessage) {
				SyntaxErrorMessage syntaxErrorMessage = (SyntaxErrorMessage)error;
				SyntaxException cause = syntaxErrorMessage.getCause();
				//
				throw new ResultCodeException(CoreResultCode.GROOVY_SCRIPT_SYNTAX_VALIDATION,
						ImmutableMap.of("message", cause.getOriginalMessage(), "line", cause.getLine()), e);
			}
			//
			throw new ResultCodeException(CoreResultCode.GROOVY_SCRIPT_VALIDATION, e);
		} catch (CompilationFailedException ex) {
			throw new ResultCodeException(CoreResultCode.GROOVY_SCRIPT_VALIDATION, ex);
		}
	}

	private Script getScript(String source) {
		// TODO: consider hashing source in order to not waste so much space
		ValueWrapper value = cacheManager.getValue(CACHE_NAME, source);
		if (value == null) {
			Script script = buildScript(source);
			cacheManager.cacheValue(CACHE_NAME, source, script);
			return script;
		}
		return (Script) value.get();
	}

	private Script buildScript(String source) {
		CompilerConfiguration compilerConfiguration = new CompilerConfiguration();
		compilerConfiguration.setVerbose(false);
		compilerConfiguration.setDebug(false);
		compilerConfiguration.addCompilationCustomizers(new SandboxTransformer());
		//
		GroovyShell shell = new GroovyShell(compilerConfiguration);
		return shell.parse(source);
	}
	
}
