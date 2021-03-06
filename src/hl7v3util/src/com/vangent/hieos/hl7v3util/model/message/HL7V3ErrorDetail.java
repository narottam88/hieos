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
package com.vangent.hieos.hl7v3util.model.message;

/**
 *
 * @author Bernie Thuman
 */
public class HL7V3ErrorDetail {

    private String code = null;
    private String text = null;

    private HL7V3ErrorDetail() {
        // Do not allow.
    }

    /**
     *
     * @param text
     */
    public HL7V3ErrorDetail(String text) {
        this.text = text;
        this.code = null;
    }

    /**
     *
     * @param text
     * @param code
     */
    public HL7V3ErrorDetail(String text, String code) {
        this.text = text;
        this.code = code;
    }

    /**
     *
     * @return
     */
    public String getCode() {
        return code;
    }

    /**
     *
     * @return
     */
    public String getText() {
        return text;
    }
}
