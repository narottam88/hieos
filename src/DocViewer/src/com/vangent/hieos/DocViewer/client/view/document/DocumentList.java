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
package com.vangent.hieos.DocViewer.client.view.document;

import com.smartgwt.client.types.SelectionStyle;
import com.smartgwt.client.widgets.Canvas;
import com.smartgwt.client.widgets.ImgButton;
import com.smartgwt.client.widgets.grid.CellFormatter;
import com.smartgwt.client.widgets.grid.ListGrid;
import com.smartgwt.client.widgets.grid.ListGridField;
import com.smartgwt.client.widgets.grid.ListGridRecord;
import com.smartgwt.client.widgets.grid.events.SelectionChangedHandler;
import com.smartgwt.client.widgets.grid.events.SelectionEvent;
import com.smartgwt.client.widgets.layout.HLayout;
import com.smartgwt.client.types.Alignment;
import com.smartgwt.client.types.ListGridFieldType;
import com.smartgwt.client.types.SelectionAppearance;
import com.smartgwt.client.widgets.events.ClickEvent;
import com.smartgwt.client.widgets.events.ClickHandler;
import com.smartgwt.client.widgets.events.DoubleClickHandler;
import com.smartgwt.client.widgets.events.DoubleClickEvent;
import com.vangent.hieos.DocViewer.client.model.document.DocumentMetadataRecord;

public class DocumentList extends Canvas {
	private final ListGrid documentListGrid;
	private final DocumentViewContainer parentContainer;

	/**
	 * 
	 * @param parent
	 * @wbp.parser.constructor
	 */
	public DocumentList(DocumentViewContainer parent) {
		this.documentListGrid = new ListGrid() {
			@Override
			protected Canvas createRecordComponent(
					final ListGridRecord iListGridRecord, final Integer iColNum) {
				String fieldName = this.getFieldName(iColNum);
				if (fieldName.equals("zoom_field")) {
					HLayout recordCanvas = new HLayout(3);
					recordCanvas.setHeight(22);
					recordCanvas.setAlign(Alignment.CENTER);
					ImgButton zoomButton = new ImgButton();
					zoomButton.setShowDown(false);
					zoomButton.setShowRollOver(false);
					zoomButton.setLayoutAlign(Alignment.CENTER);
					zoomButton.setSrc("zoom.png");
					zoomButton.setPrompt("Show document in expanded window");
					zoomButton.setHeight(16);
					zoomButton.setWidth(16);
					zoomButton.addClickHandler(new ClickHandler() {
						public void onClick(ClickEvent event) {
							DocumentMetadataRecord record = (DocumentMetadataRecord) iListGridRecord;
							parentContainer.showDocumentWindow(record
									.getDocumentMetadata());
						}
					});

					recordCanvas.addMember(zoomButton);
					return recordCanvas;

				} else {
					return null;
				}

			}
		};

		this.parentContainer = parent;

		documentListGrid.setSelectionAppearance(SelectionAppearance.ROW_STYLE);
		documentListGrid.setSelectionType(SelectionStyle.SINGLE);
		documentListGrid.setShowAllRecords(true);
		documentListGrid.setWidth(570);
		documentListGrid.setHeight(208);
		documentListGrid.setTooltip("Double-click to open document in new tab");
		documentListGrid.setWrapCells(true);
		documentListGrid.setFixedRecordHeights(false);
		documentListGrid.setShowRecordComponents(true);
		documentListGrid.setShowRecordComponentsByCell(true);

		// Creation Date:
		final ListGridField creationDateField = new ListGridField(
				"creation_date", "Creation Date", 80);
		creationDateField.setType(ListGridFieldType.DATE);
		creationDateField.setAlign(Alignment.LEFT);
		creationDateField.setCellFormatter(new CellFormatter() {
			public String format(Object value, ListGridRecord record,
					int rowNum, int colNum) {
				if (record == null)
					return null;
				DocumentMetadataRecord metadataRecord = (DocumentMetadataRecord) record;
				return metadataRecord.getFormattedCreationTime();
			}
		});

		// Title:
		final ListGridField titleField = new ListGridField("title", "Title",
				100);
		titleField.setType(ListGridFieldType.TEXT);

		// Type Code:
		final ListGridField typeCodeField = new ListGridField("type_code",
				"Type Code", 150);
		typeCodeField.setType(ListGridFieldType.TEXT);
		
		// Type Code:
		final ListGridField mimeTypeField = new ListGridField("mime_type",
				"Mime Type", 100);
		mimeTypeField.setType(ListGridFieldType.TEXT);


		// Author Institution:
		final ListGridField institutionField = new ListGridField(
				"author_institution", "Institution", 100);
		institutionField.setType(ListGridFieldType.TEXT);

		// Author Name:
		/*final ListGridField authorNameField = new ListGridField("author_name",
				"Author", 100);
		authorNameField.setType(ListGridFieldType.TEXT);*/

		// Setup the zoom field.
		ListGridField zoomField = new ListGridField("zoom_field", "Zoom", 35);
		zoomField.setAlign(Alignment.CENTER);
		zoomField.setType(ListGridFieldType.TEXT);

		documentListGrid.addDoubleClickHandler(new DoubleClickHandler() {
			public void onDoubleClick(DoubleClickEvent event) {
				DocumentMetadataRecord metadataRecord = (DocumentMetadataRecord) documentListGrid
						.getSelectedRecord();
				if (metadataRecord != null) {
					parentContainer.showDocument(metadataRecord
							.getDocumentMetadata());
				}
			}
		});

		documentListGrid.addSelectionChangedHandler(new SelectionChangedHandler() {
			@Override
			public void onSelectionChanged(SelectionEvent event) {
				DocumentMetadataRecord metadataRecord = (DocumentMetadataRecord) documentListGrid
						.getSelectedRecord();
				if (metadataRecord != null) {
					parentContainer.showDocumentDetails(metadataRecord
							.getDocumentMetadata());
				}
			}
		});

		documentListGrid.setFields(new ListGridField[] { creationDateField, titleField,
				institutionField, typeCodeField, zoomField, mimeTypeField });
		addChild(documentListGrid);
	}

	/**
	 * 
	 * @param message
	 */
	public void setLoadingDataMessage(String message) {
		documentListGrid.setLoadingDataMessage(message);
	}

	/**
	 * 
	 * @param gridRecords
	 */
	public void update(ListGridRecord[] gridRecords) {
		// Window.alert("Here !!!");
		documentListGrid.setData(gridRecords);
		/*if (gridRecords.length > 0) {
			listGrid.selectRecord(0);
		}*/
	}
	
	/**
	 * 
	 * @param metadataRecord
	 */
	public void selectRecord(DocumentMetadataRecord metadataRecord)
	{
		documentListGrid.selectRecord(metadataRecord);
	}
}