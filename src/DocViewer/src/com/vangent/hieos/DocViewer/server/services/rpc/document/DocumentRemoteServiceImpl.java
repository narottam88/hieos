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
package com.vangent.hieos.DocViewer.server.services.rpc.document;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

import org.apache.axiom.om.OMElement;

import com.google.gwt.user.server.rpc.RemoteServiceServlet;
import com.vangent.hieos.DocViewer.client.exception.RemoteServiceException;
import com.vangent.hieos.DocViewer.client.model.authentication.AuthenticationContext;
import com.vangent.hieos.DocViewer.client.model.authentication.Credentials;
import com.vangent.hieos.DocViewer.client.model.config.Config;
import com.vangent.hieos.DocViewer.client.model.document.DocumentAuthorMetadata;
import com.vangent.hieos.DocViewer.client.model.document.DocumentMetadata;
import com.vangent.hieos.DocViewer.client.model.document.DocumentSearchCriteria;
import com.vangent.hieos.DocViewer.client.model.patient.Patient;
import com.vangent.hieos.DocViewer.client.model.patient.PatientUtil;
import com.vangent.hieos.DocViewer.client.services.rpc.DocumentRemoteService;
import com.vangent.hieos.DocViewer.server.framework.ServletUtilMixin;
import com.vangent.hieos.DocViewer.server.gateway.InitiatingGateway;
import com.vangent.hieos.DocViewer.server.gateway.InitiatingGatewayFactory;
import com.vangent.hieos.xutil.exception.MetadataException;
import com.vangent.hieos.xutil.exception.MetadataValidationException;
import com.vangent.hieos.xutil.exception.SOAPFaultException;
import com.vangent.hieos.xutil.exception.XdsException;
import com.vangent.hieos.xutil.template.TemplateUtil;
import com.vangent.hieos.xutil.xconfig.XConfig;
import com.vangent.hieos.xutil.xconfig.XConfigActor;
import com.vangent.hieos.xutil.xua.utils.XUAObject;
import com.vangent.hieos.xutil.metadata.structure.Metadata;
import com.vangent.hieos.xutil.metadata.structure.MetadataParser;
import com.vangent.hieos.xutil.metadata.structure.MetadataSupport;
import com.vangent.hieos.xutil.hl7.date.Hl7Date;

/**
 * 
 * @author Bernie Thuman
 * 
 */
public class DocumentRemoteServiceImpl extends RemoteServiceServlet implements
		DocumentRemoteService {

	/**
	 * 
	 */
	private static final long serialVersionUID = 3195773598502538894L;
	private final ServletUtilMixin servletUtil = new ServletUtilMixin();

	static final String PROP_ADHOCQUERY_SINGLEPID_TEMPLATE = "AdhocQuerySinglePIDTemplate";
	static final String PROP_CONTENT_URL = "ContentURL";

	/**
	 * 
	 */
	@Override
	public void init() {
		// Initialize servlet.
		servletUtil.init(this.getServletContext());
	}

	/**
	 * 
	 */
	@Override
	public List<DocumentMetadata> findDocuments(AuthenticationContext authCtxt,
			DocumentSearchCriteria criteria) throws RemoteServiceException {

		// See if we have a valid session ...
		HttpServletRequest request = this.getThreadLocalRequest();
		boolean validSession = ServletUtilMixin.isValidSession(request);
		if (!validSession) {
			throw new RemoteServiceException("Invalid Session!");
		}

		ServletContext servletContext = this.getServletContext();

		// First build the query message (from a template).
		System.out.println("DocViewer::findDocuments - target PID = "
				+ criteria.getPatient().getPatientID());
		OMElement query = this.getAdhocQuerySinglePID(servletContext,
				criteria.getPatient());
		List<DocumentMetadata> documentMetadataList = new ArrayList<DocumentMetadata>();
		try {
			if (query != null) {
				// Get the proper initiating gateway configuration.
				String searchMode = criteria.getSearchMode();
				InitiatingGateway ig = InitiatingGatewayFactory
						.getInitiatingGateway(searchMode, servletUtil);

				// Issue Document Retrieve ...
				System.out.println("Doc Query ...");

				// FIXME: Move this code.
				XConfigActor igConfig = ig.getIGConfig();
				if (igConfig.getPropertyAsBoolean("XUAEnabled")) {
					XUAObject xuaObj = this.getXUAObject(authCtxt, ig,
							InitiatingGateway.TransactionType.DOC_QUERY);
					OMElement samlClaimsNode = this.getSAMLClaims(authCtxt,
							criteria.getPatient());
					// System.out.println("SAML Claims: " +
					// samlClaimsNode.toString());
					xuaObj.setClaims(samlClaimsNode);
					ig.setXuaObject(xuaObj);
				}

				OMElement response = ig.soapCall(
						InitiatingGateway.TransactionType.DOC_QUERY, query);
				if (response != null) // TBD: Need to check for errors!!!!
				{
					// Convert the response into value objects.
					this.loadDocumentMetadataList(documentMetadataList,
							response);
				}
			}
		} catch (SOAPFaultException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (XdsException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("Returning ...");
		return documentMetadataList;
	}

	/**
	 * 
	 * @param documentMetadataList
	 * @param response
	 * @throws MetadataException
	 * @throws MetadataValidationException
	 */
	private void loadDocumentMetadataList(
			List<DocumentMetadata> documentMetadataList, OMElement response)
			throws MetadataException, MetadataValidationException {

		// Parse the SOAP response to get Metadata instance.
		Metadata m = MetadataParser.parseNonSubmission(response);

		// Loop through all ExtrinsicObjects (Documents) and do conversion to
		// value objects.
		List<OMElement> extrinsicObjects = m.getExtrinsicObjects();
		System.out.println("# of documents: " + extrinsicObjects.size());
		for (OMElement extrinsicObject : extrinsicObjects) {
			System.out.println("Document found!!!!");
			DocumentMetadata documentMetadata = this.buildDocumentMetadata(m,
					extrinsicObject);
			documentMetadataList.add(documentMetadata);
		}
	}

	/**
	 * 
	 * @param m
	 * @param extrinsicObject
	 * @return
	 * @throws MetadataException
	 */
	private DocumentMetadata buildDocumentMetadata(Metadata m,
			OMElement extrinsicObject) {

		// Create the DocumentMetadata instance.
		DocumentMetadata documentMetadata = new DocumentMetadata();

		// Document id.
		String documentID;
		try {
			documentID = m.getExternalIdentifierValue(m.getId(extrinsicObject),
					MetadataSupport.XDSDocumentEntry_uniqueid_uuid);
		} catch (MetadataException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			documentID = "UNKNOWN";
		}
		documentMetadata.setDocumentID(documentID);

		// Patient id.
		String patientID;
		try {
			patientID = m.getExternalIdentifierValue(m.getId(extrinsicObject),
					MetadataSupport.XDSDocumentEntry_patientid_uuid);
		} catch (MetadataException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			patientID = "UNKNOWN";
		}
		documentMetadata.setEuid(PatientUtil.getIDFromPIDString(patientID));
		documentMetadata.setAssigningAuthority(PatientUtil
				.getAssigningAuthorityFromPIDString(patientID));

		// Repository id.
		String repositoryID = m.getSlotValue(extrinsicObject,
				"repositoryUniqueId", 0);
		documentMetadata.setRepositoryID(repositoryID);

		// Home Community id.
		String homeCommunityID = m.getHome(extrinsicObject);
		documentMetadata.setHomeCommunityID(homeCommunityID);

		// Creation time.
		String creationTime = m
				.getSlotValue(extrinsicObject, "creationTime", 0);
		documentMetadata.setCreationTime(Hl7Date.toDate(creationTime));

		// Name (Title?).
		String name = m.getNameValue(extrinsicObject);
		documentMetadata.setTitle(name);

		// Mime Type.
		String mimeType = extrinsicObject
				.getAttributeValue(MetadataSupport.mime_type_qname);
		documentMetadata.setMimeType(mimeType);

		// Size.
		String sizeString = m.getSlotValue(extrinsicObject, "size", 0);
		int size = -1;
		if (sizeString != null) {
			size = new Integer(sizeString);
		}
		documentMetadata.setSize(size);

		// Authors.
		List<DocumentAuthorMetadata> authors = this.getAuthors(m,
				extrinsicObject);
		documentMetadata.setAuthors(authors);

		// Class Code, Format Code, Type Code.
		documentMetadata.setClassCode(this.getClassCode(m, extrinsicObject));
		documentMetadata.setFormatCode(this.getFormatCode(m, extrinsicObject));
		documentMetadata.setTypeCode(this.getTypeCode(m, extrinsicObject));

		// To allow retrieval by client.
		documentMetadata.setContentURL(servletUtil
				.getProperty(PROP_CONTENT_URL));

		return documentMetadata;
	}

	/**
	 * 
	 * @param m
	 * @param extrinsicObject
	 * @return
	 */
	private List<DocumentAuthorMetadata> getAuthors(Metadata m,
			OMElement extrinsicObject) {
		List<DocumentAuthorMetadata> documentAuthors = new ArrayList<DocumentAuthorMetadata>();
		try {
			List<OMElement> authorNodes = m.getClassifications(extrinsicObject,
					MetadataSupport.XDSDocumentEntry_author_uuid);
			for (OMElement authorNode : authorNodes) {
				String authorPerson = m.getSlotValue(authorNode,
						"authorPerson", 0);

				// FIXME: Just get first 1 (for now) .. can be multiple.
				String authorInstitution = m.getSlotValue(authorNode,
						"authorInstitution", 0);

				// Now create and load the DocumentAuthorMetadata instance.
				DocumentAuthorMetadata authorMetadata = new DocumentAuthorMetadata();
				authorMetadata.setName(authorPerson);
				authorMetadata.setInstitution(authorInstitution);
				documentAuthors.add(authorMetadata);
			}
		} catch (MetadataException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return documentAuthors;
	}

	/**
	 * 
	 * @param m
	 * @param extrinsicObject
	 * @return
	 */
	private String getClassCode(Metadata m, OMElement extrinsicObject) {
		return this.getCodeDisplayName(m, extrinsicObject,
				MetadataSupport.XDSDocumentEntry_classCode_uuid);
	}

	/**
	 * 
	 * @param m
	 * @param extrinsicObject
	 * @return
	 */
	private String getFormatCode(Metadata m, OMElement extrinsicObject) {
		return this.getCodeDisplayName(m, extrinsicObject,
				MetadataSupport.XDSDocumentEntry_formatCode_uuid);
	}

	/**
	 * 
	 * @param m
	 * @param extrinsicObject
	 * @return
	 */
	private String getTypeCode(Metadata m, OMElement extrinsicObject) {
		// FIXME: Add classification to MetadataSupport.
		return this.getCodeDisplayName(m, extrinsicObject,
				"urn:uuid:f0306f51-975f-434e-a61c-c59651d33983");
	}

	/**
	 * 
	 * @param m
	 * @param extrinsicObject
	 * @param classificationScheme
	 * @return
	 */
	private String getCodeDisplayName(Metadata m, OMElement extrinsicObject,
			String classificationScheme) {
		String codeDisplayName = "UNKNOWN";
		try {
			List<OMElement> codeNodes = m.getClassifications(extrinsicObject,
					classificationScheme);
			if (codeNodes != null && codeNodes.size() > 0) {
				// FIXME: ? Just take first one ? Likely ok.
				OMElement codeNode = codeNodes.get(0);
				codeDisplayName = this.getCodeDisplayName(m, codeNode);
			}
		} catch (MetadataException e) {
			// Just ignore ...
		}
		return codeDisplayName;
	}

	/**
	 * 
	 * @param node
	 * @return
	 */
	private String getCodeDisplayName(Metadata m, OMElement node) {
		String codeDisplayName = m.getNameValue(node);
		if (codeDisplayName == null) {
			codeDisplayName = "UNKNOWN";
		}
		return codeDisplayName;
		/*
		 * OMElement nameNode = node.getFirstChildWithName(new QName("Name"));
		 * if (nameNode != null) { OMElement localizedStringNode = nameNode
		 * .getFirstChildWithName(new QName("LocalizedString")); if
		 * (localizedStringNode != null) { codeDisplayName = localizedStringNode
		 * .getAttributeValue(new QName("value")); } } return codeDisplayName;
		 */
	}

	/**
	 * 
	 * @param servletContext
	 * @param patient
	 * @return
	 */
	public OMElement getAdhocQuerySinglePID(ServletContext servletContext,
			Patient patient) {
		String template = servletUtil
				.getTemplateString(servletUtil
						.getProperty(DocumentRemoteServiceImpl.PROP_ADHOCQUERY_SINGLEPID_TEMPLATE));
		HashMap<String, String> replacements = new HashMap<String, String>();
		replacements.put("PID", patient.getPatientID());
		return TemplateUtil.getOMElementFromTemplate(template, replacements);
	}

	// FIXME: Move these methods to another class.

	/**
	 * 
	 * @param authCtxt
	 * @param ig
	 * @param txnType
	 * @return
	 */
	private XUAObject getXUAObject(AuthenticationContext authCtxt,
			InitiatingGateway ig, InitiatingGateway.TransactionType txnType) {
		XUAObject xuaObj = new XUAObject();
		XConfigActor igConfig = ig.getIGConfig();
		xuaObj.setXUASupportedSOAPActions(igConfig
				.getProperty("XUAEnabledSOAPActions"));
		if (!xuaObj.containsSOAPAction(ig.getSOAPAction(txnType))) {
			return null; // Early exit!
		}
		Credentials creds = authCtxt.getCredentials();
		xuaObj.setUserName(creds.getUserId());
		xuaObj.setPassword(creds.getPassword());
		xuaObj.setXUAEnabled(true);
		xuaObj.setSTSUri("http://www.vangent.com/X-ServiceProvider-HIEOS"); // FIXME?
		XConfigActor stsConfig = this.getSTSConfig();
		String stsEndpointURL = stsConfig.getTransaction("IssueToken")
				.getEndpointURL();
		System.out.println("STS endpoint URL: " + stsEndpointURL);
		xuaObj.setSTSUrl(stsEndpointURL);
		// Claims to be filled in later.
		// xuaObj.setClaims(null);

		return xuaObj;
	}

	/**
	 * 
	 * @return
	 */
	public XConfigActor getSTSConfig() {
		return servletUtil.getActorConfig("sts", XConfig.STS_TYPE);
	}

	// FIXME: Complete .. remove hard-coded values and pull from authCtxt where
	// applicable.

	/**
	 * 
	 * @param authCtxt
	 * @param patient
	 * @return
	 */
	public OMElement getSAMLClaims(AuthenticationContext authCtxt,
			Patient patient) {
		String template = servletUtil.getTemplateString(servletUtil
				.getProperty(Config.KEY_SAML_CLAIMS_TEMPLATE));
		HashMap<String, String> replacements = new HashMap<String, String>();
		// SUBJECT_ID
		replacements.put("SUBJECT_ID", authCtxt.getCredentials().getUserId());
		// SUBJECT_ORGANIZATION_ID
		replacements.put("SUBJECT_ORGANIZATION_ID", "^^^^^^^^^1.1.1");
		// SUBJECT_ORGANIZATION
		replacements.put("SUBJECT_ORGANIZATION", "GDIT");
		// SUBJECT_PURPOSE_OF_USE
		replacements.put("SUBJECT_PURPOSE_OF_USE", "TREATMENT");
		// SUBJECT_ROLE
		replacements.put("SUBJECT_ROLE", "DOCTOR");
		// RESOURCE_ID = Patient ID (CX formatted)
		System.out.println("SAML Claims RESOURCE_ID = "
				+ patient.getPatientID());
		replacements.put("RESOURCE_ID", patient.getPatientID());
		return TemplateUtil.getOMElementFromTemplate(template, replacements);
	}

}
