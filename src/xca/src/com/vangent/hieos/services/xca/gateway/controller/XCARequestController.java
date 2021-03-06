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
package com.vangent.hieos.services.xca.gateway.controller;

import com.vangent.hieos.xutil.metadata.structure.MetadataSupport;
import com.vangent.hieos.xutil.response.Response;
import com.vangent.hieos.xutil.xlog.client.XLogMessage;

// Exceptions.
import com.vangent.hieos.xutil.exception.SOAPFaultException;

// Third-party.
import java.util.ArrayList;
import java.util.Iterator;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;
import org.apache.axiom.om.OMElement;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.axis2.context.MessageContext;
import org.apache.log4j.Logger;

/**
 *
 * @author Bernie Thuman
 */
public class XCARequestController {

    private final static Logger logger = Logger.getLogger(XCARequestController.class);
    private Response response;
    private XLogMessage logMessage;
    // Key = uniqueId (homeCommunityId or repositoryUniqueId), Value = XCAAbstractRequestCollection
    private HashMap requests = new HashMap();
    static ExecutorService executor = null;  // Only one of these shared across all web requests.

    /**
     *
     * @param transactionContext
     */
    public XCARequestController(Response response, XLogMessage logMessage) {
        this.response = response;
        this.logMessage = logMessage;
    }

    /**
     *
     * @return
     */
    private ExecutorService getExecutor() {
        if (executor == null) {
            // Shared across all web service requests.
            executor = Executors.newCachedThreadPool();
        }
        return executor;
    }

    /**
     * 
     * @param callableResult
     * @return
     */
    // SYNCHRONIZED!
    private synchronized Future<XCAAbstractRequestCollection> submit(GatewayOutboundRequest callableResult) {
        ExecutorService exec = this.getExecutor();
        Future<XCAAbstractRequestCollection> future = exec.submit(callableResult);
        return future;
    }

    /**
     *
     * @param uniqueId
     * @return
     */
    public XCAAbstractRequestCollection getRequestCollection(String uniqueId) {
        XCAAbstractRequestCollection requestCollection = null;
        if (requests.containsKey(uniqueId)) {
            requestCollection = (XCAAbstractRequestCollection) requests.get(uniqueId);
        }
        return requestCollection;
    }

    /**
     *
     * @param requestCollection
     */
    @SuppressWarnings("unchecked")
    public void setRequestCollection(XCAAbstractRequestCollection requestCollection) {
        requests.put(requestCollection.getUniqueId(), requestCollection);
    }

    /**
     *
     * @param uniqueId
     * @param request
     */
    public void addRequest(String uniqueId, XCARequest request) {
        XCAAbstractRequestCollection requestCollection = this.getRequestCollection(uniqueId);
        if (requestCollection != null) {
            requestCollection.addRequest(request);
        } else {
            // Throw exception (FIXME).
        }
    }

    /**
     * 
     * @return
     */
    public ArrayList<OMElement> sendRequests() {
        ArrayList<OMElement> results = new ArrayList<OMElement>();
        Collection allRequests = requests.values();

        boolean XCAMultiThread = true;  // FIXME: Place into XConfig.
        boolean multiThreadMode = false;
        ArrayList<Future<XCAAbstractRequestCollection>> futures = null;
        int taskSize = allRequests.size();
        if (XCAMultiThread == true && taskSize > 1) {  // FIXME: Task bound size should be configurable.
            // Do multi-threading.
            multiThreadMode = true;
            futures = new ArrayList<Future<XCAAbstractRequestCollection>>();
        }
        logger.debug("*** multiThreadMode = " + multiThreadMode + " ***");

        // Get current thread's message context.
        MessageContext currentMessageContext = MessageContext.getCurrentMessageContext();

        // Submit work to be conducted in parallel (if required):
        for (Iterator it = allRequests.iterator(); it.hasNext();) {
            
            // Each pass is for a single entity (Responding Gateway / Repository / Registry).
            XCAAbstractRequestCollection requestCollection = (XCAAbstractRequestCollection) it.next();
            requestCollection.setParentThreadMessageContext(currentMessageContext);
            GatewayOutboundRequest outboundRequest = new GatewayOutboundRequest(requestCollection);
            if (multiThreadMode == true) {
                Future<XCAAbstractRequestCollection> future = this.submit(outboundRequest);
                futures.add(future);
            } else {
                // Not in multi-thread mode.
                try {
                    // Just call in the current thread.

                    requestCollection = outboundRequest.call();
                    this.processOutboundRequestResult(requestCollection, results);
                } catch (Exception ex) {
                    logger.error("XCA EXCEPTION ... continuing", ex);
                }
            }
        }

        // If in mult-thread mode, wait for futures ...
        if (multiThreadMode == true) {
            for (Future<XCAAbstractRequestCollection> future : futures) {
                try {
                    XCAAbstractRequestCollection requestCollection = future.get();  // Will block until ready.
                    logger.debug("*** FINISHED THREAD - " + requestCollection.getUniqueId());
                    this.processOutboundRequestResult(requestCollection, results);
                } catch (InterruptedException ex) {
                    logger.error("XCA EXCEPTION ... continuing", ex);
                } catch (ExecutionException ex) {
                    logger.error("XCA EXCEPTION ... continuing", ex);
                }
            }
        }
        return results;
    }

    /**
     *
     * @param requestCollection
     * @param results
     */
    private void processOutboundRequestResult(
            XCAAbstractRequestCollection requestCollection,
            ArrayList<OMElement> results) {
        // Update RegistryErrorList here.
        ArrayList<XCAErrorMessage> errors = requestCollection.getErrors();
        for (Iterator it = errors.iterator(); it.hasNext();) {
            XCAErrorMessage errorMessage = (XCAErrorMessage) it.next();
            response.add_error(errorMessage.getCode(),
                    errorMessage.getMessage(),
                    errorMessage.getLocation(),
                    logMessage);
        }
        // Update the results collection (and log if necessary).
        OMElement result = requestCollection.getResult();
        if (result != null) {
            results.add(result);
            if (logMessage.isLogEnabled()) {
                logMessage.addOtherParam("Result (" + requestCollection.getEndpointURL() + ")", result);
            }
        }
    }

    /**
     * 
     */
    public class GatewayOutboundRequest implements Callable<XCAAbstractRequestCollection> {

        private XCAAbstractRequestCollection requestCollection;

        /**
         *
         * @param requestCollection
         */
        public GatewayOutboundRequest(XCAAbstractRequestCollection requestCollection) {
            this.requestCollection = requestCollection;
        }

        /**
         * 
         * @return
         * @throws Exception
         */
        @Override
        public XCAAbstractRequestCollection call() throws Exception {
            try {
                logger.debug("*** IN CALLABLE - " + requestCollection.getUniqueId());
                OMElement result = requestCollection.sendRequests();
                // Do nothing with result as it is cached in the RequestCollection.

                // BHT (FIXUP) -- need to find proper exceptions to return.
            } catch (SOAPFaultException e) {
                logger.error("+++ EXCEPTION = " + e.getMessage());

                String errorString = String.format("Failure contacting community or repository = %s and returned the following error message: %s.",
                        requestCollection.getUniqueId(), e.getMessage());
                XCAErrorMessage errorMessage = new XCAErrorMessage(
                        MetadataSupport.XDSUnavailableCommunity,
                        errorString,
                        requestCollection.getUniqueId());
                requestCollection.addErrorMessage(errorMessage);
            } finally {
                logger.debug("*** FINISHED CALLABLE - " + requestCollection.getUniqueId());
            }
            return requestCollection;
        }
    }
}
