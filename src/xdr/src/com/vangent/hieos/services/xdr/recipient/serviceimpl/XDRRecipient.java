/*
 * This code is subject to the HIEOS License, Version 1.0
 *
 * Copyright(c) 2008-2009 Vangent, Inc.  All rights reserved.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.vangent.hieos.services.xdr.recipient.serviceimpl;

import com.vangent.hieos.xutil.exception.XdsValidationException;
import com.vangent.hieos.xutil.metadata.structure.MetadataSupport;
import com.vangent.hieos.services.xdr.recipient.transactions.ProcessXDRPackage;
import com.vangent.hieos.xutil.atna.ATNAAuditEvent;

import org.apache.axis2.AxisFault;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMNamespace;

import org.apache.log4j.Logger;

import com.vangent.hieos.xutil.exception.SOAPFaultException;
import com.vangent.hieos.xutil.services.framework.XAbstractService;
import com.vangent.hieos.xutil.xconfig.XConfig;
import com.vangent.hieos.xutil.xconfig.XConfigActor;
import com.vangent.hieos.xutil.xconfig.XConfigObject;

/**
 * Processes XDR Transactions 
 *
 * @author Adeola Odunlami
 */
public class XDRRecipient extends XAbstractService {

    private final static Logger logger = Logger.getLogger(XDRRecipient.class);
    private static XConfigActor config = null;  // Singleton.

    @Override
    protected XConfigActor getConfigActor() {
        return config;
    }

    /**
     *
     */
    public XDRRecipient() {
        super();
    }

    /**
     * Parse and Process the XDR Request
     * @param xdr
     * @return
     * @throws org.apache.axis2.AxisFault
     */
    public OMElement XDRDocRecipientRequest(OMElement xdr) throws AxisFault {
        if (logger.isDebugEnabled()) {
            logger.debug("XDR Request Received: " + xdr.toString());
        }
        OMElement response = null;
        try {
            beginTransaction(getXDRTransactionName(), xdr);

            // Validate the XDR package
            validateWS();
            validateMTOM();
            validateXDRTransaction(xdr);

            // Process the Request
            ProcessXDRPackage s = new ProcessXDRPackage(log_message);
            s.setConfigActor(config);
            response = s.processXDRPackage(xdr);

            endTransaction(s.getStatus());
        } catch (SOAPFaultException ex) {
            throwAxisFault(ex);
        } catch (XdsValidationException ex) {
            response = endTransaction(xdr, ex, XAbstractService.ActorType.DOCRECIPIENT, "");
        }
        return response;
    }

    protected String getXDRTransactionName() {
        return "XDRRecipient";
    }

    /**
     * Checks the XDR request contains as XDS.b transaction
     * @param xdr
     * @throws XdsValidationException
     */
    private void validateXDRTransaction(OMElement xdr) throws XdsValidationException {
        OMNamespace ns = xdr.getNamespace();
        String ns_uri = ns.getNamespaceURI();
        if (ns_uri == null || !ns_uri.equals(MetadataSupport.xdsB.getNamespaceURI())) {
            throw new XdsValidationException("Invalid namespace on " + xdr.getLocalName() + " (" + ns_uri + ")");
        }
    }

    /**
     * This will be called during the deployment time of the service.
     * Irrespective of the service scope this method will be called
     */
    @Override
    public void startup() {
        logger.info("XDRRecipient::startup()");
        try {
            XConfig xconf;
            xconf = XConfig.getInstance();
            XConfigObject homeCommunity = xconf.getHomeCommunityConfig();
            config = (XConfigActor) homeCommunity.getXConfigObjectWithName("docrecipient", XConfig.XDR_DOCUMENT_RECIPIENT_TYPE);
        } catch (Exception ex) {
            logger.fatal("Unable to get configuration for service", ex);
        }
        this.ATNAlogStart(ATNAAuditEvent.ActorType.DOCRECIPIENT);
    }

    /**
     * This will be called during the system shut down time. Irrespective
     * of the service scope this method will be called
     */
    @Override
    public void shutdown() {
        logger.info("XDRRecipient::shutdown()");
        this.ATNAlogStop(ATNAAuditEvent.ActorType.DOCRECIPIENT);
    }
}
