/*
 * This code is subject to the HIEOS License, Version 1.0
 *
 * Copyright(c) 2012 Vangent, Inc.  All rights reserved.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.vangent.hieos.services.xds.registry.mu.validation;

import com.vangent.hieos.services.xds.registry.backend.BackendRegistry;
import com.vangent.hieos.services.xds.registry.mu.command.MetadataUpdateCommand;
import com.vangent.hieos.services.xds.registry.mu.command.UpdateFolderMetadataCommand;
import com.vangent.hieos.services.xds.registry.storedquery.MetadataUpdateStoredQuerySupport;
import com.vangent.hieos.xutil.exception.XDSMetadataVersionException;
import com.vangent.hieos.xutil.exception.XdsException;
import com.vangent.hieos.xutil.metadata.structure.Metadata;
import com.vangent.hieos.xutil.metadata.structure.MetadataParser;
import com.vangent.hieos.xutil.metadata.structure.MetadataSupport;
import java.util.List;
import org.apache.axiom.om.OMElement;

/**
 *
 * @author Bernie Thuman
 */
public class UpdateFolderMetadataCommandValidator extends MetadataUpdateCommandValidator {

    /**
     * 
     * @param metadataUpdateCommand
     */
    public UpdateFolderMetadataCommandValidator(MetadataUpdateCommand metadataUpdateCommand) {
        super(metadataUpdateCommand);
    }

    /**
     * 
     * @throws XdsException
     */
    public boolean validate() throws XdsException {
        UpdateFolderMetadataCommand cmd = (UpdateFolderMetadataCommand) this.getMetadataUpdateCommand();
        boolean validationSuccess = true;

        // Get metadata update context for use later.
        //MetadataUpdateContext metadataUpdateContext = cmd.getMetadataUpdateContext();
        //XLogMessage logMessage = metadataUpdateContext.getLogMessage();
        //BackendRegistry backendRegistry = metadataUpdateContext.getBackendRegistry();
        //RegistryResponse registryResponse = metadataUpdateContext.getRegistryResponse();
        //XConfigActor configActor = metadataUpdateContext.getConfigActor();

        //
        // Look for an existing document that 1) matches the lid, 2) status is "Approved"
        // and 3) matches the previous version.
        //
        this.getCurrentRegistryObject(cmd);

        // Run further validations.
        Metadata submittedMetadata = cmd.getSubmittedMetadata();
        this.validateUniqueIdMatch(cmd.getSubmittedRegistryObject(), submittedMetadata, cmd.getCurrentRegistryObject(), cmd.getCurrentMetadata());
        return validationSuccess;
    }

    /**
     *
     * @param cmd
     * @throws XdsException
     */
    private void getCurrentRegistryObject(UpdateFolderMetadataCommand cmd) throws XdsException {
        BackendRegistry backendRegistry = cmd.getMetadataUpdateContext().getBackendRegistry();
        MetadataUpdateStoredQuerySupport muSQ = cmd.getMetadataUpdateContext().getStoredQuerySupport();
        Metadata submittedMetadata = cmd.getSubmittedMetadata();
        OMElement submittedRegistryObject = cmd.getSubmittedRegistryObject();
        String previousVersion = cmd.getPreviousVersion();
        String lid = submittedMetadata.getLID(submittedRegistryObject);

        // Attempt to find existing folder.
        muSQ.setReturnLeafClass(true);
        backendRegistry.setReason("Locate Previous Approved Folder (by LID/Version)");
        OMElement queryResult = muSQ.getFoldersByLID(lid, MetadataSupport.status_type_approved, previousVersion);
        backendRegistry.setReason("");

        // Convert response into Metadata instance.
        Metadata currentMetadata = MetadataParser.parseNonSubmission(queryResult);
        //Metadata currentMetadata = cmd.getCurrentMetadata();
        List<OMElement> currentFolders = currentMetadata.getFolders();
        if (currentFolders.isEmpty()) {
            throw new XDSMetadataVersionException("Existing approved folder entry not found for lid="
                    + lid + ", version=" + previousVersion);
        } else if (currentFolders.size() > 1) {
            throw new XDSMetadataVersionException("> 1 approved folder entry found for lid="
                    + lid + ", version=" + previousVersion);
        }

        // Fall through: we found a folder that matches.

        // Set current metadata and registry object in command instance.
        cmd.setCurrentMetadata(currentMetadata);
        cmd.setCurrentRegistryObject(currentMetadata.getFolder(0));
    }
}
