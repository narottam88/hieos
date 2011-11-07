/*
 * This code is subject to the HIEOS License, Version 1.0
 *
 * Copyright(c) 2011 Vangent, Inc.  All rights reserved.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.vangent.hieos.empi.config;

import com.vangent.hieos.empi.match.MatchAlgorithm;
import com.vangent.hieos.services.pixpdq.empi.exception.EMPIException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.log4j.Logger;

/**
 *
 * @author Bernie Thuman
 */
public class EMPIConfig {

    private final static Logger logger = Logger.getLogger(EMPIConfig.class);
    private static String JNDI_RESOURCE_NAME = "jndi-resource-name";
    private static String MATCH_ALGORITHM = "match-algorithm";
    private static String DEFAULT_JNDI_RESOURCE_NAME = "jdbc/hieos-empi";
    private static String TRANSFORM_FUNCTIONS = "transform-functions.transform-function";
    private static String DISTANCE_FUNCTIONS = "distance-functions.distance-function";
    private static String FIELDS = "fields.field";
    private static String BLOCKING_CONFIG = "blocking-config(0)";
    private static String MATCH_CONFIG = "match-config(0)";
    private static String EUID_CONFIG = "euid-config(0)";
    private static EMPIConfig _instance = null;
    private BlockingConfig blockingConfig;
    private MatchConfig matchConfig;
    private String jndiResourceName;
    private MatchAlgorithm matchAlgorithm;
    private EUIDConfig euidConfig;
    private Map<String, TransformFunctionConfig> transformFunctionConfigs = new HashMap<String, TransformFunctionConfig>();
    private Map<String, DistanceFunctionConfig> distanceFunctionConfigs = new HashMap<String, DistanceFunctionConfig>();
    private Map<String, FieldConfig> fieldConfigs = new HashMap<String, FieldConfig>();

    /**
     *
     */
    private EMPIConfig() {
        // Do not allow.
    }

    /**
     * 
     * @return
     * @throws EMPIException
     */
    static public synchronized EMPIConfig getInstance() throws EMPIException {
        if (_instance == null) {
            _instance = new EMPIConfig();
            _instance.loadConfiguration();
        }
        return _instance;
    }

    /**
     *
     * @return
     */
    public MatchConfig getMatchConfig() {
        return matchConfig;
    }

    /**
     *
     * @return
     */
    public BlockingConfig getBlockingConfig() {
        return blockingConfig;
    }

    /**
     *
     * @return
     */
    public String getJndiResourceName() {
        return jndiResourceName;
    }

    /**
     *
     * @return
     */
    public MatchAlgorithm getMatchAlgorithm() {
        return matchAlgorithm;
    }

    /**
     *
     * @return
     */
    public EUIDConfig getEuidConfig() {
        return euidConfig;
    }

    /**
     *
     * @return
     */
    public Map<String, DistanceFunctionConfig> getDistanceFunctionConfigs() {
        return distanceFunctionConfigs;
    }

    /**
     *
     * @param name
     * @return
     */
    public DistanceFunctionConfig getDistanceFunctionConfig(String name) {
        return distanceFunctionConfigs.get(name);
    }

    /**
     *
     * @return
     */
    public Map<String, TransformFunctionConfig> getTransformFunctionConfigs() {
        return transformFunctionConfigs;
    }

    /**
     *
     * @param name
     * @return
     */
    public TransformFunctionConfig getTransformFunctionConfig(String name) {
        return transformFunctionConfigs.get(name);
    }

    /**
     * 
     * @return
     */
    public Map<String, FieldConfig> getFieldConfigs() {
        return fieldConfigs;
    }

    /**
     *
     * @param name
     * @return
     */
    public FieldConfig getFieldConfig(String name) {
        return fieldConfigs.get(name);
    }

    /**
     * 
     * @return
     */
    public List<FieldConfig> getFieldConfigList() {
        return new ArrayList<FieldConfig>(fieldConfigs.values());
    }

    /**
     * 
     * @throws EMPIException
     */
    private void loadConfiguration() throws EMPIException {
        // FIXME: DO NOT HARDWIRE LOCATION - put in XConfig.java
        String configLocation = "c:/dev/hieos/config/empi/empiConfig.xml";
        try {
            XMLConfiguration xmlConfig = new XMLConfiguration(configLocation);
            jndiResourceName = xmlConfig.getString(JNDI_RESOURCE_NAME, DEFAULT_JNDI_RESOURCE_NAME);

            // Load the match algorithm.
            this.loadMatchAlgorithm(xmlConfig);

            // Load function configurations.
            this.loadFunctionConfigs(xmlConfig);

            // Load field configurations.
            this.loadFieldConfigs(xmlConfig);

            // Load blocking configuration.
            blockingConfig = new BlockingConfig();
            blockingConfig.load(xmlConfig.configurationAt(BLOCKING_CONFIG), this);

            // Load matching configuration.
            matchConfig = new MatchConfig();
            matchConfig.load(xmlConfig.configurationAt(MATCH_CONFIG), this);

            // Load EUID configuration.
            euidConfig = new EUIDConfig();
            euidConfig.load(xmlConfig.configurationAt(EUID_CONFIG), this);

        } catch (ConfigurationException ex) {
            throw new EMPIException(
                    "EMPIConfig: Could not load configuration from " + configLocation + " " + ex.getMessage());
        }
    }

    /**
     *
     * @param hc
     */
    private void loadFunctionConfigs(HierarchicalConfiguration hc) {

        // Get transform function configurations.
        List transformFunctions = hc.configurationsAt(TRANSFORM_FUNCTIONS);
        for (Iterator it = transformFunctions.iterator(); it.hasNext();) {
            HierarchicalConfiguration hcSub = (HierarchicalConfiguration) it.next();
            TransformFunctionConfig transformFunctionConfig = new TransformFunctionConfig();
            try {
                transformFunctionConfig.load(hcSub, this);
                // Keep track of transform function configurations (in hash map).
                transformFunctionConfigs.put(transformFunctionConfig.getName(), transformFunctionConfig);
            } catch (EMPIException ex) {
                // FIXME? Keep processing
            }
        }

        // Get distance function configurations.
        List distanceFunctions = hc.configurationsAt(DISTANCE_FUNCTIONS);
        for (Iterator it = distanceFunctions.iterator(); it.hasNext();) {
            HierarchicalConfiguration hcSub = (HierarchicalConfiguration) it.next();
            DistanceFunctionConfig distanceFunctionConfig = new DistanceFunctionConfig();
            try {
                distanceFunctionConfig.load(hcSub, this);
                // Keep track of distance function configurations (in hash map).
                distanceFunctionConfigs.put(distanceFunctionConfig.getName(), distanceFunctionConfig);
            } catch (EMPIException ex) {
                // FIXME? Keep processing
            }
        }
    }

    /**
     * 
     * @param hc
     * @throws EMPIException
     */
    private void loadFieldConfigs(HierarchicalConfiguration hc) throws EMPIException {
        // Get field configurations.
        List fields = hc.configurationsAt(FIELDS);
        for (Iterator it = fields.iterator(); it.hasNext();) {
            HierarchicalConfiguration hcSub = (HierarchicalConfiguration) it.next();
            FieldConfig fieldConfig = new FieldConfig();
            fieldConfig.load(hcSub, this);
            // Keep track of field configurations (in hash map).
            fieldConfigs.put(fieldConfig.getName(), fieldConfig);
        }
    }

    /**
     *
     * @param hc
     * @throws EMPIException
     */
    private void loadMatchAlgorithm(HierarchicalConfiguration hc) throws EMPIException {
        String matchAlgorithmClassName = hc.getString(MATCH_ALGORITHM);
        // Get an instance of the match algorithm.
        this.matchAlgorithm = (MatchAlgorithm) ConfigHelper.loadClass(matchAlgorithmClassName);
    }
}
