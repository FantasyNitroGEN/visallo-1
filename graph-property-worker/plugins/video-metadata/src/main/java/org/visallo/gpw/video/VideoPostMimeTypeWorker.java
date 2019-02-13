package org.visallo.gpw.video;

import com.google.inject.Inject;
import org.json.JSONObject;
import org.vertexium.Authorizations;
import org.vertexium.Metadata;
import org.vertexium.Vertex;
import org.vertexium.mutation.ExistingElementMutation;
import org.visallo.core.ingest.graphProperty.GraphPropertyWorkData;
import org.visallo.core.ingest.graphProperty.GraphPropertyWorkerPrepareData;
import org.visallo.core.ingest.graphProperty.PostMimeTypeWorker;
import org.visallo.core.model.Description;
import org.visallo.core.model.Name;
import org.visallo.core.model.ontology.OntologyRepository;
import org.visallo.core.model.properties.types.DoubleVisalloProperty;
import org.visallo.core.model.properties.types.IntegerVisalloProperty;
import org.visallo.core.model.properties.types.VisalloPropertyUpdate;
import org.visallo.core.model.properties.types.*;
import org.visallo.core.util.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.visallo.core.model.ontology.OntologyRepository.PUBLIC;

@Name("Video Metadata")
@Description("Extracts video metadata using FFProbe")
public class VideoPostMimeTypeWorker extends PostMimeTypeWorker {
    public static final String MULTI_VALUE_PROPERTY_KEY = VideoPostMimeTypeWorker.class.getName();
    private ProcessRunner processRunner;
    private OntologyRepository ontologyRepository;
    private DoubleVisalloProperty duration;
    private GeoPointVisalloProperty geoLocation;
    private DateVisalloProperty dateTaken;
    private StringVisalloProperty deviceMaker;
    private StringVisalloProperty deviceModel;
    private IntegerVisalloProperty width;
    private IntegerVisalloProperty height;
    private StringVisalloProperty mediaMetadata;
    private IntegerVisalloProperty clockwiseRotation;
    private IntegerVisalloProperty fileSize;

    @Override
    public void prepare(GraphPropertyWorkerPrepareData workerPrepareData) throws Exception {
        super.prepare(workerPrepareData);
        duration = new DoubleVisalloProperty(ontologyRepository.getRequiredPropertyIRIByIntent("media.duration", PUBLIC));
        geoLocation = new GeoPointVisalloProperty(ontologyRepository.getRequiredPropertyIRIByIntent("geoLocation", PUBLIC));
        dateTaken = new DateVisalloProperty(ontologyRepository.getRequiredPropertyIRIByIntent("media.dateTaken", PUBLIC));
        deviceMaker = new StringVisalloProperty(ontologyRepository.getRequiredPropertyIRIByIntent("media.deviceMake", PUBLIC));
        deviceModel = new StringVisalloProperty(ontologyRepository.getRequiredPropertyIRIByIntent("media.deviceModel", PUBLIC));
        width = new IntegerVisalloProperty(ontologyRepository.getRequiredPropertyIRIByIntent("media.width", PUBLIC));
        height = new IntegerVisalloProperty(ontologyRepository.getRequiredPropertyIRIByIntent("media.height", PUBLIC));
        mediaMetadata = new StringVisalloProperty(ontologyRepository.getRequiredPropertyIRIByIntent("media.metadata", PUBLIC));
        clockwiseRotation = new IntegerVisalloProperty(ontologyRepository.getRequiredPropertyIRIByIntent("media.clockwiseRotation", PUBLIC));
        fileSize = new IntegerVisalloProperty(ontologyRepository.getRequiredPropertyIRIByIntent("media.fileSize", PUBLIC));
    }

    @Override
    public void execute(String mimeType, GraphPropertyWorkData data, Authorizations authorizations) throws Exception {
        if (!mimeType.startsWith("video")) {
            return;
        }

        File localFile = getLocalFileForRaw(data.getElement());
        JSONObject videoMetadata = FFprobeExecutor.getJson(processRunner, localFile.getAbsolutePath());
        ExistingElementMutation<Vertex> m = data.getElement().prepareMutation();
        List<VisalloPropertyUpdate> changedProperties = new ArrayList<>();
        //Metadata metadata = data.createPropertyMetadata();
        Metadata metadata = new Metadata();
        if (videoMetadata != null) {
            setProperty(duration, FFprobeDurationUtil.getDuration(videoMetadata), m, metadata, data, changedProperties);
            // TODO: why no geolocation on uploaded video?
            setProperty(geoLocation, FFprobeGeoLocationUtil.getGeoPoint(videoMetadata), m, metadata, data, changedProperties);
            setProperty(dateTaken, FFprobeDateUtil.getDateTaken(videoMetadata), m, metadata, data, changedProperties);
            setProperty(deviceMaker, FFprobeMakeAndModelUtil.getMake(videoMetadata), m, metadata, data, changedProperties);
            setProperty(deviceModel, FFprobeMakeAndModelUtil.getModel(videoMetadata), m, metadata, data, changedProperties);
            setProperty(width, FFprobeDimensionsUtil.getWidth(videoMetadata), m, metadata, data, changedProperties);
            setProperty(height, FFprobeDimensionsUtil.getHeight(videoMetadata), m, metadata, data, changedProperties);
            setProperty(this.mediaMetadata, videoMetadata.toString(), m, metadata, data, changedProperties);
            setProperty(clockwiseRotation, FFprobeVideoFiltersUtil.getRotation(videoMetadata), m, metadata, data, changedProperties);
        }

        setProperty(fileSize, FileSizeUtil.getSize(localFile), m, metadata, data, changedProperties);

        m.save(authorizations);
        getGraph().flush();
        getWorkQueueRepository().pushGraphVisalloPropertyQueue(data.getElement(), changedProperties, data.getPriority());
    }

    private <T> void setProperty(VisalloProperty<T, T> property, T value, ExistingElementMutation<Vertex> mutation,
                                 Metadata metadata, GraphPropertyWorkData data,
                                 List<VisalloPropertyUpdate> changedProperties) {
        property.updateProperty(changedProperties, data.getElement(), mutation, MULTI_VALUE_PROPERTY_KEY, value,
                metadata, data.getVisibility());
    }

    @Inject
    public void setProcessRunner(ProcessRunner processRunner) {
        this.processRunner = processRunner;
    }

    @Inject
    public void setOntologyRepository(OntologyRepository ontologyRepository) {
        this.ontologyRepository = ontologyRepository;
    }
}
