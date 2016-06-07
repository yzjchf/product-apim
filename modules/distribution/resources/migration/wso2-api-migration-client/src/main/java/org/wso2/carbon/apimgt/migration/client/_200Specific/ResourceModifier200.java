/*
* Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
* WSO2 Inc. licenses this file to you under the Apache License,
* Version 2.0 (the "License"); you may not use this file except
* in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied. See the License for the
* specific language governing permissions and limitations
* under the License.
*
*/
package org.wso2.carbon.apimgt.migration.client._200Specific;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.wso2.carbon.apimgt.migration.APIMigrationException;
import org.wso2.carbon.apimgt.migration.client._110Specific.dto.SynapseDTO;
import org.wso2.carbon.apimgt.migration.util.Constants;
import org.wso2.carbon.apimgt.migration.util.SynapseUtil;

import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.nio.charset.Charset;
import java.util.List;

public class ResourceModifier200 {
    private static final Log log = LogFactory.getLog(ResourceModifier200.class);
    public static void updateSynapseConfigs(List<SynapseDTO> synapseDTOs) {

        for (SynapseDTO synapseDTO : synapseDTOs) {
            Element existingThrottleHandler = SynapseUtil
                    .getHandler(synapseDTO.getDocument(), Constants.SYNAPSE_API_VALUE_THROTTLE_HANDLER);
            Element handlersElement = (Element) synapseDTO.getDocument()
                    .getElementsByTagNameNS(Constants.SYNAPSE_API_XMLNS, Constants.SYNAPSE_API_ELEMENT_HANDLERS)
                    .item(0);

            if (existingThrottleHandler != null && !isThrottleHandlerUpdated(existingThrottleHandler)) {
                Element updatedThrottleHandler = SynapseUtil
                        .createHandler(synapseDTO.getDocument(), Constants.NEW_SYNAPSE_API_VALUE_THROTTLE_HANDLER,
                                null);
                handlersElement.replaceChild(updatedThrottleHandler, existingThrottleHandler);
            }

            Element existingLatencyStatHandler = SynapseUtil
                    .getHandler(synapseDTO.getDocument(), Constants.SYNAPSE_API_VALUE_LATENCY_STATS_HANDLER);
            if (existingLatencyStatHandler != null) {
                log.info("handler '" + Constants.SYNAPSE_API_VALUE_LATENCY_STATS_HANDLER + "' already exist in "
                        + synapseDTO.getFile().getAbsolutePath());
            } else {
                Element newAPIMgtLatencyStatsHandler = synapseDTO.getDocument()
                        .createElementNS(Constants.SYNAPSE_API_XMLNS, Constants.SYNAPSE_API_ELEMENT_HANDLER);
                newAPIMgtLatencyStatsHandler.setAttribute(Constants.SYNAPSE_API_ATTRIBUTE_CLASS,
                        Constants.SYNAPSE_API_VALUE_LATENCY_STATS_HANDLER);
                handlersElement.insertBefore(newAPIMgtLatencyStatsHandler, handlersElement.getFirstChild());
            }
            try {
                updateResources(synapseDTO.getDocument());
            } catch (APIMigrationException e) {
                log.error("error occcurend while migrating synapse at " + synapseDTO.getFile().getAbsolutePath(), e);
            }
        }
    }

    private static boolean isThrottleHandlerUpdated(Element existingThrottleHandler) {
        NodeList properties = existingThrottleHandler.getChildNodes();

        for (int i = 0; i < properties.getLength(); ++i) {
            Node childNode = properties.item(i);

            if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                String nameAttribute = ((Element) childNode).getAttribute(Constants.SYNAPSE_API_ATTRIBUTE_NAME);

                if (nameAttribute != null) {
                    return Constants.SYNAPSE_API_VALUE_RESOURCE_KEY.equals(nameAttribute);
                }
            }
        }

        return false;
    }

    private static void updateResources(Document document) throws APIMigrationException {
        NodeList resourceNodes = document.getElementsByTagName("resource");
        for (int i = 0; i < resourceNodes.getLength(); i++) {
            Element resourceElement = (Element) resourceNodes.item(i);
            updateInSequence(resourceElement, document);
        }
    }

    private static void updateInSequence(Element resourceElement, Document doc) {
        // Find the inSequence
        Element inSequenceElement = (Element) resourceElement
                .getElementsByTagName(Constants.SYNAPSE_API_ELEMENT_INSEQUENCE).item(0);

        // Find the property element in the inSequence
        NodeList filters = inSequenceElement.getElementsByTagName(Constants.SYNAPSE_API_ELEMENT_FILTER);

        for (int j = 0; j < filters.getLength(); ++j) {
            Element filterElement = (Element) filters.item(j);
            if (Constants.SYNAPSE_API_VALUE_AM_KEY_TYPE
                    .equals(filterElement.getAttribute(Constants.SYNAPSE_API_ATTRIBUTE_SOURCE))) {
                // Only one <then> element can exist in filter mediator
                Element thenElement = (Element) filterElement
                        .getElementsByTagNameNS(Constants.SYNAPSE_API_XMLNS, Constants.SYNAPSE_API_ELEMENT_THEN)
                        .item(0);
                NodeList properties = thenElement.getElementsByTagName(Constants.SYNAPSE_API_ELEMENT_PROPERTY);
                for (int i = 0; i < properties.getLength(); ++i) {
                    Element propertyElement = (Element) properties.item(i);
                    if (propertyElement.hasAttribute(Constants.SYNAPSE_API_ATTRIBUTE_NAME)) {
                        if (Constants.SYNAPSE_API_VALUE_BACKEND_REQUEST_TIME
                                .equals(propertyElement.getAttribute(Constants.SYNAPSE_API_ATTRIBUTE_NAME))) {
                            thenElement.removeChild(propertyElement);
                            break;
                        }
                    }
                }
                // At least one <send> element must exist as a child of <then> element
            }
        }

        boolean isExistProp = false;
        NodeList properties = inSequenceElement.getElementsByTagName(Constants.SYNAPSE_API_ELEMENT_PROPERTY);
        for (int i = 0; i < properties.getLength(); ++i) {
            Element propertyElement = (Element) properties.item(i);
            if (propertyElement.hasAttribute(Constants.SYNAPSE_API_ATTRIBUTE_NAME)) {
                if (Constants.SYNAPSE_API_VALUE_BACKEND_REQUEST_TIME
                        .equals(propertyElement.getAttribute(Constants.SYNAPSE_API_ATTRIBUTE_NAME))) {
                    isExistProp = true;
                    log.info("Property '" + Constants.SYNAPSE_API_VALUE_BACKEND_REQUEST_TIME + "' already exist");
                    break;
                }
            }
        }

        if (!isExistProp) {
            Element propertyElement = doc
                    .createElementNS(Constants.SYNAPSE_API_XMLNS, Constants.SYNAPSE_API_ELEMENT_PROPERTY);
            propertyElement
                    .setAttribute(Constants.SYNAPSE_API_ATTRIBUTE_EXPRESSION, Constants.SYNAPSE_API_VALUE_EXPRESSION);
            propertyElement.setAttribute(Constants.SYNAPSE_API_ATTRIBUTE_NAME,
                    Constants.SYNAPSE_API_VALUE_BACKEND_REQUEST_TIME);
            if (filters.getLength() > 0) {
                inSequenceElement.insertBefore(propertyElement, filters.item(0));
            } else {
                inSequenceElement.appendChild(propertyElement);
            }
        }
    }

    public static void transformXMLDocument(Document document, File file) {
        document.getDocumentElement().normalize();
        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, Charset.defaultCharset().toString());
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            transformer.transform(new DOMSource(document), new StreamResult(file));
        } catch (TransformerConfigurationException e) {
            log.error("Transformer configuration error encountered while transforming file " + file.getName(), e);
        } catch (TransformerException e) {
            log.error("Transformer error encountered while transforming file " + file.getName(), e);
        }
    }
}
