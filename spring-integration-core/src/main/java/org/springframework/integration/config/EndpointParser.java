/*
 * Copyright 2002-2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.config;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.integration.MessagingConfigurationException;
import org.springframework.integration.bus.ConsumerPolicy;
import org.springframework.integration.endpoint.GenericMessageEndpoint;
import org.springframework.integration.handler.DefaultMessageHandlerAdapter;
import org.springframework.integration.handler.MessageHandlerChain;
import org.springframework.util.StringUtils;

/**
 * Parser for the <em>endpoint</em> element of the integration namespace.
 * 
 * @author Mark Fisher
 */
public class EndpointParser implements BeanDefinitionParser {

	private static final String ID_ATTRIBUTE = "id";

	private static final String INPUT_CHANNEL_ATTRIBUTE = "input-channel";

	private static final String INPUT_CHANNEL_PROPERTY = "inputChannelName";

	private static final String DEFAULT_OUTPUT_CHANNEL_ATTRIBUTE = "default-output-channel";

	private static final String DEFAULT_OUTPUT_CHANNEL_PROPERTY = "defaultOutputChannelName";

	private static final String HANDLER_ELEMENT = "handler";

	private static final String REF_ATTRIBUTE = "ref";

	private static final String METHOD_ATTRIBUTE = "method";

	private static final String HANDLERS_PROPERTY = "handlers";

	private static final String HANDLER_REF_ATTRIBUTE = "handler-ref";

	private static final String HANDLER_METHOD_ATTRIBUTE = "handler-method";

	private static final String HANDLER_PROPERTY = "handler";

	private static final String OBJECT_PROPERTY = "object";

	private static final String METHOD_NAME_PROPERTY = "methodName";

	private static final String PERIOD_ATTRIBUTE = "period";

	private static final String PERIOD_PROPERTY = "period";

	private static final String CONSUMER_ELEMENT = "consumer";

	private static final String CONSUMER_POLICY_PROPERTY = "consumerPolicy";


	public BeanDefinition parse(Element element, ParserContext parserContext) {
		RootBeanDefinition endpointDef = new RootBeanDefinition(GenericMessageEndpoint.class);
		endpointDef.setSource(parserContext.extractSource(element));
		String inputChannel = element.getAttribute(INPUT_CHANNEL_ATTRIBUTE);
		if (StringUtils.hasText(inputChannel)) {
			endpointDef.getPropertyValues().addPropertyValue(INPUT_CHANNEL_PROPERTY, inputChannel);
		}
		String defaultOutputChannel = element.getAttribute(DEFAULT_OUTPUT_CHANNEL_ATTRIBUTE);
		if (StringUtils.hasText(defaultOutputChannel)) {
			endpointDef.getPropertyValues().addPropertyValue(DEFAULT_OUTPUT_CHANNEL_PROPERTY, defaultOutputChannel);
		}
		List<String> childHandlerRefs = new ArrayList<String>();
		NodeList childNodes = element.getChildNodes();
		for (int i = 0; i < childNodes.getLength(); i++) {
			Node child = childNodes.item(i);
			if (child.getNodeType() == Node.ELEMENT_NODE) {
				String localName = child.getLocalName();
				if (CONSUMER_ELEMENT.equals(localName)) {
					String consumerBeanName = parseConsumer((Element) child, parserContext);
					endpointDef.getPropertyValues().addPropertyValue(
							CONSUMER_POLICY_PROPERTY, new RuntimeBeanReference(consumerBeanName));
				}
				else if (HANDLER_ELEMENT.equals(localName)) {
					String ref = ((Element) child).getAttribute(REF_ATTRIBUTE);
					String method = ((Element) child).getAttribute(METHOD_ATTRIBUTE);
					childHandlerRefs.add(this.parseHandlerAdapter(ref, method, parserContext));
				}
			}
		}
		if (childHandlerRefs.size() > 0) {
			if (childHandlerRefs.size() == 1) {
				endpointDef.getPropertyValues().addPropertyValue(
						HANDLER_PROPERTY, new RuntimeBeanReference(childHandlerRefs.get(0)));
			}
			else {
				RootBeanDefinition handlerChainDef = new RootBeanDefinition(MessageHandlerChain.class);
				List handlerList = new ManagedList();
				for (String ref : childHandlerRefs) {
					handlerList.add(new RuntimeBeanReference(ref));
				}
				handlerChainDef.getPropertyValues().addPropertyValue(HANDLERS_PROPERTY, handlerList);
				String chainBeanName = parserContext.getReaderContext().generateBeanName(handlerChainDef);
				parserContext.registerBeanComponent(new BeanComponentDefinition(handlerChainDef, chainBeanName));
				endpointDef.getPropertyValues().addPropertyValue(HANDLER_PROPERTY, new RuntimeBeanReference(chainBeanName));
			}
		}
		String handlerRef = element.getAttribute(HANDLER_REF_ATTRIBUTE);
		if (StringUtils.hasText(handlerRef)) {
			if (childHandlerRefs.size() > 0) {
				throw new MessagingConfigurationException(
						"The 'handler-ref' attribute is only supported when no 'handler' child elements are present");
			}
			String handlerMethod = element.getAttribute(HANDLER_METHOD_ATTRIBUTE);
			if (StringUtils.hasText(handlerMethod)) {
				String adapterBeanName = this.parseHandlerAdapter(handlerRef, handlerMethod, parserContext);
				endpointDef.getPropertyValues().addPropertyValue(HANDLER_PROPERTY, new RuntimeBeanReference(adapterBeanName));
			}
			else {
				endpointDef.getPropertyValues().addPropertyValue(HANDLER_PROPERTY, new RuntimeBeanReference(handlerRef));
			}
		}
		String beanName = element.getAttribute(ID_ATTRIBUTE);
		if (!StringUtils.hasText(beanName)) {
			beanName = parserContext.getReaderContext().generateBeanName(endpointDef);
		}
		parserContext.registerBeanComponent(new BeanComponentDefinition(endpointDef, beanName));		
		return endpointDef;
	}

	private String parseConsumer(Element element, ParserContext parserContext) {
		RootBeanDefinition consumerDef = new RootBeanDefinition(ConsumerPolicy.class);
		String period = element.getAttribute(PERIOD_ATTRIBUTE);
		if (StringUtils.hasText(period)) {
			consumerDef.getPropertyValues().addPropertyValue(PERIOD_PROPERTY, Integer.parseInt(period));
		}
		String beanName = parserContext.getReaderContext().generateBeanName(consumerDef);
		parserContext.registerBeanComponent(new BeanComponentDefinition(consumerDef, beanName));
		return beanName;
	}

	private String parseHandlerAdapter(String handlerRef, String handlerMethod, ParserContext parserContext) {
		BeanDefinition handlerAdapterDef = new RootBeanDefinition(DefaultMessageHandlerAdapter.class);
		handlerAdapterDef.getPropertyValues().addPropertyValue(OBJECT_PROPERTY, new RuntimeBeanReference(handlerRef));
		handlerAdapterDef.getPropertyValues().addPropertyValue(METHOD_NAME_PROPERTY, handlerMethod);
		String adapterBeanName = parserContext.getReaderContext().generateBeanName(handlerAdapterDef);
		parserContext.registerBeanComponent(new BeanComponentDefinition(handlerAdapterDef, adapterBeanName));
		return adapterBeanName;
	}

}
