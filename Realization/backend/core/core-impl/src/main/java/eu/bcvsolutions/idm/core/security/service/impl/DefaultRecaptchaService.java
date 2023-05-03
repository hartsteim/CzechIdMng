package eu.bcvsolutions.idm.core.security.service.impl;

import java.net.Proxy;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.google.common.collect.ImmutableMap;

import eu.bcvsolutions.idm.core.api.config.domain.RecaptchaConfiguration;
import eu.bcvsolutions.idm.core.api.domain.CoreResultCode;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.security.api.dto.RecaptchaRequest;
import eu.bcvsolutions.idm.core.security.api.dto.RecaptchaResponse;
import eu.bcvsolutions.idm.core.security.api.service.RecaptchaService;

/**
 * Service for evaluating ReCaptcha.
 * 
 * @author Filip Mestanek
 * @author Radek Tomiška
 */
@Service("recaptchaService")
public class DefaultRecaptchaService implements RecaptchaService {

	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(DefaultRecaptchaService.class);

	@Autowired @Lazy private RestTemplate restTemplate;
	@Autowired private RecaptchaConfiguration recaptchaConfiguration;

	@Override
	public RecaptchaResponse checkRecaptcha(RecaptchaRequest req) {
		Proxy proxy = recaptchaConfiguration.getProxy();
		RestTemplate newRestTemplate = null;
		if (proxy == null) {
			newRestTemplate = this.restTemplate;
			LOG.debug("ReCaptcha will not use proxy conffiguration.");
		} else {
			SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
			requestFactory.setProxy(proxy);
			newRestTemplate = new RestTemplate(requestFactory);
			LOG.debug("ReCaptcha will use proxy conffiguration: [{}].", proxy.address().toString());
		}

		// If i tried to send is as an Entity, google API didn't understand the message
		// and returned errors. Therefore this map.
		MultiValueMap<String, String> body = createBody(recaptchaConfiguration.getSecretKey().asString(), req.getRemoteIp(), req.getResponse());
		
        try {
        	RecaptchaResponse response = newRestTemplate
        			.postForEntity(recaptchaConfiguration.getUrl(), body, RecaptchaResponse.class)
                    .getBody();
        	if (!response.getErrorCodes().isEmpty()) {
        		throw new ResultCodeException(CoreResultCode.RECAPTCHA_CHECK_FAILED,
    					ImmutableMap.of("errors", StringUtils.join(response.getErrorCodes(), ",")));
        	}
            return response;
        } catch (RestClientException ex) {
            throw new ResultCodeException(CoreResultCode.RECAPTCHA_SERVICE_UNAVAILABLE, ex);
        }
	}
	
	private MultiValueMap<String, String> createBody(String secret, String remoteIp, String response) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", secret);
        form.add("remoteip", remoteIp);
        form.add("response", response);
        return form;
    }
}
