package org.visallo.zipCodeResolver;

import com.google.inject.Inject;
import org.vertexium.*;
import org.vertexium.type.GeoPoint;
import org.visallo.core.ingest.graphProperty.TermMentionFilter;
import org.visallo.core.ingest.graphProperty.TermMentionFilterPrepareData;
import org.visallo.core.model.Description;
import org.visallo.core.model.Name;
import org.visallo.core.model.ontology.OntologyRepository;
import org.visallo.core.model.properties.VisalloProperties;
import org.visallo.core.model.termMention.TermMentionBuilder;
import org.visallo.core.model.workspace.WorkspaceRepository;
import org.visallo.core.user.User;
import org.visallo.web.clientapi.model.VisibilityJson;

import java.util.Set;

@Name("ZipCode Resolver")
@Description("Resolves ZipCodes")
public class ZipCodeResolverTermMentionFilter extends TermMentionFilter {
    public static final String MULTI_VALUE_PROPERTY_KEY = ZipCodeResolverTermMentionFilter.class.getName();
    private String zipCodeConceptIri;
    private String zipCodePropertyIri;
    private String cityPropertyIri;
    private String statePropertyIri;
    private String geoLocationIri;
    private String artifactHasEntityIri;
    private WorkspaceRepository workspaceRepository;
    private OntologyRepository ontologyRepository;
    private ZipCodeRepository zipCodeRepository;
    private User user;

    @Override
    public void prepare(TermMentionFilterPrepareData termMentionFilterPrepareData) throws Exception {
        super.prepare(termMentionFilterPrepareData);

        prepareIris();
        user = termMentionFilterPrepareData.getUser();
    }

    public void prepareIris() {
        zipCodeConceptIri = ontologyRepository.getRequiredConceptIRIByIntent("zipCode");
        zipCodePropertyIri = ontologyRepository.getRequiredPropertyIRIByIntent("zipCode");
        cityPropertyIri = ontologyRepository.getPropertyIRIByIntent("city");
        statePropertyIri = ontologyRepository.getPropertyIRIByIntent("state");
        geoLocationIri = ontologyRepository.getRequiredPropertyIRIByIntent("geoLocation");
        artifactHasEntityIri = ontologyRepository.getRequiredRelationshipIRIByIntent("artifactHasEntity");
    }

    @Override
    public void apply(Vertex outVertex, final Iterable<Vertex> termMentions, final Authorizations authorizations) throws Exception {
        for (Vertex termMention : termMentions) {
            if (!zipCodeConceptIri.equals(VisalloProperties.TERM_MENTION_CONCEPT_TYPE.getPropertyValue(termMention))) {
                continue;
            }

            String text = VisalloProperties.TERM_MENTION_TITLE.getPropertyValue(termMention);
            if (text.indexOf('-') > 0) {
                text = text.substring(0, text.indexOf('-'));
            }

            ZipCodeEntry zipCodeEntry = zipCodeRepository.find(text);
            if (zipCodeEntry == null) {
                continue;
            }

            String id = String.format("GEO-ZIPCODE-%s", zipCodeEntry.getZipCode());
            VisibilityJson outVertexVisibilityJson = VisalloProperties.VISIBILITY_JSON.getPropertyValue(outVertex);
            Metadata metadata = new Metadata();
            VisalloProperties.VISIBILITY_JSON_METADATA.setMetadata(metadata, outVertexVisibilityJson, getVisibilityTranslator().getDefaultVisibility());
            GeoPoint geoPoint = zipCodeEntry.createGeoPoint();
            ElementBuilder<Vertex> resolvedToVertexBuilder = getGraph().prepareVertex(id, outVertex.getVisibility())
                    .addPropertyValue(MULTI_VALUE_PROPERTY_KEY, geoLocationIri, geoPoint, metadata, outVertex.getVisibility());
            VisalloProperties.CONCEPT_TYPE.setProperty(resolvedToVertexBuilder, zipCodeConceptIri, metadata, outVertex.getVisibility());
            VisalloProperties.SOURCE.addPropertyValue(resolvedToVertexBuilder, MULTI_VALUE_PROPERTY_KEY, "Zip Code Resolver", metadata, outVertex.getVisibility());
            VisalloProperties.VISIBILITY_JSON.setProperty(resolvedToVertexBuilder, outVertexVisibilityJson, metadata, outVertex.getVisibility());
            resolvedToVertexBuilder.addPropertyValue(MULTI_VALUE_PROPERTY_KEY, zipCodePropertyIri, zipCodeEntry.getZipCode(), metadata, outVertex.getVisibility());
            if (cityPropertyIri != null) {
                resolvedToVertexBuilder.addPropertyValue(MULTI_VALUE_PROPERTY_KEY, cityPropertyIri, zipCodeEntry.getCity(), metadata, outVertex.getVisibility());
            }
            if (statePropertyIri != null) {
                resolvedToVertexBuilder.addPropertyValue(MULTI_VALUE_PROPERTY_KEY, statePropertyIri, zipCodeEntry.getState(), metadata, outVertex.getVisibility());
            }
            Vertex zipCodeVertex = resolvedToVertexBuilder.save(authorizations);
            getGraph().flush();

            String edgeId = outVertex.getId() + "-" + artifactHasEntityIri + "-" + zipCodeVertex.getId();
            Edge resolvedEdge = getGraph().prepareEdge(edgeId, outVertex, zipCodeVertex, artifactHasEntityIri, outVertex.getVisibility()).save(authorizations);
            VisalloProperties.VISIBILITY_JSON.setProperty(resolvedEdge, outVertexVisibilityJson, metadata, outVertex.getVisibility(), authorizations);
            VisibilityJson visibilityJson = VisalloProperties.TERM_MENTION_VISIBILITY_JSON.getPropertyValue(termMention);
            if (visibilityJson != null && visibilityJson.getWorkspaces().size() > 0) {
                Set<String> workspaceIds = visibilityJson.getWorkspaces();
                for (String workspaceId : workspaceIds) {
                    workspaceRepository.updateEntityOnWorkspace(workspaceRepository.findById(workspaceId, user), id, false, null, user);
                }
            }

            String title = String.format("%s - %s, %s", zipCodeEntry.getZipCode(), zipCodeEntry.getCity(), zipCodeEntry.getState());
            new TermMentionBuilder(termMention, outVertex)
                    .resolvedTo(zipCodeVertex, resolvedEdge)
                    .title(title)
                    .conceptIri(zipCodeConceptIri)
                    .process(getClass().getName())
                    .visibilityJson(VisalloProperties.TERM_MENTION_VISIBILITY_JSON.getPropertyValue(termMention))
                    .save(getGraph(), getVisibilityTranslator(), user, authorizations);
        }
    }

    @Inject
    public void setWorkspaceRepository(WorkspaceRepository workspaceRepository) {
        this.workspaceRepository = workspaceRepository;
    }

    @Inject
    public void setOntologyRepository(OntologyRepository ontologyRepository) {
        this.ontologyRepository = ontologyRepository;
    }

    @Inject
    public void setZipCodeRepository(ZipCodeRepository zipCodeRepository) {
        this.zipCodeRepository = zipCodeRepository;
    }
}
