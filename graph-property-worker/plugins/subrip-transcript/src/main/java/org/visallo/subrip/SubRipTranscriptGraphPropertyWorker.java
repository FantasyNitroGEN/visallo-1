package org.visallo.subrip;

import org.vertexium.Element;
import org.vertexium.Metadata;
import org.vertexium.Property;
import org.vertexium.Vertex;
import org.vertexium.mutation.ExistingElementMutation;
import org.vertexium.property.StreamingPropertyValue;
import org.visallo.core.ingest.graphProperty.GraphPropertyWorkData;
import org.visallo.core.ingest.graphProperty.GraphPropertyWorker;
import org.visallo.core.ingest.video.VideoTranscript;
import org.visallo.core.model.Description;
import org.visallo.core.model.Name;
import org.visallo.core.model.properties.VisalloProperties;
import org.visallo.gpw.video.SubRip;

import java.io.InputStream;

@Name("Sub-rip")
@Description("Extracts sub-rip transcripts")
public class SubRipTranscriptGraphPropertyWorker extends GraphPropertyWorker {
    private static final String PROPERTY_KEY = SubRipTranscriptGraphPropertyWorker.class.getName();

    @Override
    public void execute(InputStream in, GraphPropertyWorkData data) throws Exception {
        StreamingPropertyValue youtubeccValue = SubRipTranscriptFileImportSupportingFileHandler.SUBRIP_CC.getOnlyPropertyValue(data.getElement());
        VideoTranscript videoTranscript = SubRip.read(youtubeccValue.getInputStream());

        ExistingElementMutation<Vertex> m = data.getElement().prepareMutation();
        //Metadata metadata = data.createPropertyMetadata();
        Metadata metadata = data.createPropertyMetadata(getUser());
        VisalloProperties.TEXT_DESCRIPTION_METADATA.setMetadata(metadata, "Sub-rip Transcript", getVisibilityTranslator().getDefaultVisibility());
        addVideoTranscriptAsTextPropertiesToMutation(m, PROPERTY_KEY, videoTranscript, metadata, data.getVisibility());
        m.save(getAuthorizations());

        getGraph().flush();
        pushVideoTranscriptTextPropertiesOnWorkQueue(data.getElement(), PROPERTY_KEY, videoTranscript, data.getPriority());
    }


    @Override
    public boolean isHandled(Element element, Property property) {
        if (property == null) {
            return false;
        }

        StreamingPropertyValue subripValue = SubRipTranscriptFileImportSupportingFileHandler.SUBRIP_CC.getOnlyPropertyValue(element);
        if (subripValue == null) {
            return false;
        }

        if (!property.getName().equals(VisalloProperties.RAW.getPropertyName())) {
            return false;
        }
        String mimeType = (String) property.getMetadata().getValue(VisalloProperties.MIME_TYPE.getPropertyName());
        if (mimeType == null || !mimeType.startsWith("video")) {
            return false;
        }

        return true;
    }
}
