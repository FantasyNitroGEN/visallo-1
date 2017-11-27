define([
    'create-react-class',
    'prop-types',
    './OpenLayers',
    './clusterHover',
    'configuration/plugins/registry',
    'components/RegistryInjectorHOC',
    'util/vertex/formatters',
    'util/deepObjectCache',
    'util/mapConfig'
], function(createReactClass, PropTypes, OpenLayers, clusterHover, registry, RegistryInjectorHOC, F, DeepObjectCache, mapConfig) {
    'use strict';

    const REQUEST_UPDATE_DEBOUNCE = 300;

    /**
     * @deprecated Use {@link org.visallo.product.toolbar.item} instead
     */
    registry.documentExtensionPoint('org.visallo.map.options',
        'Add components to the map options toolbar',
        function(e) {
            return ('identifier' in e) && ('optionComponentPath' in e);
        },
        'http://docs.visallo.org/extension-points/front-end/mapOptions'
    );

    /**
     * Extension to style map features/pins using the
     * [OpenLayers](http://openlayers.org)
     * [`ol.style.Style`](http://openlayers.org/en/latest/apidoc/ol.style.Style.html)
     * api.
     *
     * This does not change clustered features.
     *
     * @param {org.visallo.map.style~canHandle} canHandle Function that
     * determines if style function applies for elements.
     * @param {org.visallo.map.style~style} style Style to use for feature
     * @param {org.visallo.map.style~style} selectedStyle Style to use when feature is selected
     * @example
     * registry.registerExtension('org.visallo.map.style', {
     *     canHandle: function(productEdgeInfo, element) {
     *         return element.properties.length > 2;
     *     },
     *     style: function(productEdgeInfo, element) {
     *         const fill = new ol.style.Fill({ color: '#ff0000' })
     *         const stroke = new ol.style.Stroke({ color: '#0000ff', width: 2 })
     *         return new ol.style.Style({
     *             image: new ol.style.Circle({
     *                 fill: fill,
     *                 stroke: stroke,
     *                 radius: 25
     *             })
     *         })
     *     }
     * });
     */
    registry.documentExtensionPoint('org.visallo.map.style',
        'Style map features using OpenLayers',
        function(e) {
            return _.isFunction(e.canHandle) && (_.isFunction(e.style) || _.isFunction(e.selectedStyle))
        },
        'http://docs.visallo.org/extension-points/front-end/mapStyle'
    );

    const Map = createReactClass({

        propTypes: {
            configProperties: PropTypes.object.isRequired,
            onUpdateViewport: PropTypes.func.isRequired,
            onSelectElements: PropTypes.func.isRequired,
            onVertexMenu: PropTypes.func.isRequired,
            elements: PropTypes.shape({ vertices: PropTypes.object, edges: PropTypes.object })
        },

        getInitialState() {
            return { viewport: this.props.viewport, generatePreview: true }
        },

        render() {
            const { viewport, generatePreview } = this.state;
            const { product, onSelectElements, onUpdatePreview } = this.props;
            const { clusterFeatures, ancillaryFeatures } = this.mapElementsToFeatures();

            return (
                <div style={{height:'100%'}} ref={r => {this.wrap = r}}>
                <OpenLayers
                    ref={c => {this._openlayers = c}}
                    product={product}
                    features={clusterFeatures}
                    below={ancillaryFeatures}
                    viewport={viewport}
                    generatePreview={generatePreview}
                    panelPadding={this.props.panelPadding}
                    clearCaches={this.requestUpdateDebounce}
                    onTap={this.onTap}
                    onPan={this.onViewport}
                    onZoom={this.onViewport}
                    onContextTap={this.onContextTap}
                    onSelectElements={onSelectElements}
                    onMouseOver={this.onMouseOver}
                    onMouseOut={this.onMouseOut}
                    onUpdatePreview={onUpdatePreview.bind(this, this.props.product.id)}
                    {...mapConfig()}
                />
                </div>
            )
        },

        shouldComponentUpdate(nextProps) {
            const onlyViewportChanged = Object.keys(nextProps).every(key => {
                if (key === 'viewport') {
                    return true;
                }
                return this.props[key] === nextProps[key];
            })

            if (onlyViewportChanged) {
                return false;
            }
            return true;
        },

        componentWillReceiveProps(nextProps) {
            if (nextProps.product.id === this.props.product.id) {
                this.setState({ viewport: {}, generatePreview: false })
            } else {
                this.saveViewport(this.props)
                this.setState({ viewport: nextProps.viewport || {}, generatePreview: true })
            }
        },

        componentWillMount() {
            this.caches = {
                canHandle: new DeepObjectCache(),
                style: new DeepObjectCache(),
                selectedStyle: new DeepObjectCache()
            };
            this.requestUpdateDebounce = _.debounce(this.clearCaches, REQUEST_UPDATE_DEBOUNCE)
        },

        componentDidMount() {
            $(this.wrap).on('selectAll', (event) => {
                this.props.onSelectAll(this.props.product.id);
            })
            $(document).on('elementsCut.org-visallo-map', (event, { vertexIds }) => {
                this.props.onRemoveElementIds({ vertexIds, edgeIds: [] });
            })
            $(document).on('elementsPasted.org-visallo-map', (event, elementIds) => {
                this.props.onDropElementIds(elementIds)
            })

            this.saveViewportDebounce = _.debounce(this.saveViewport, 250);

            this.legacyListeners({
                fileImportSuccess: { node: $('.products-full-pane.visible')[0], handler: (event, { vertexIds }) => {
                    this.props.onDropElementIds({vertexIds});
                }}
            })
        },

        componentWillUnmount() {
            this.removeEvents.forEach(({ node, func, events }) => {
                $(node).off(events, func);
            });

            $(this.wrap).off('selectAll');
            $(document).off('.org-visallo-map');
            this.saveViewport(this.props)
        },

        onTap({map, pixel}) {
            if (!map.hasFeatureAtPixel(pixel)) {
                this.props.onClearSelection();
            }
        },

        onMouseOver(ol, map, features) {
            const cluster = features && features[0];
            if (cluster.get('coordinates').length > 1) {
                clusterHover.show(ol, map, cluster, this._openlayers._featureStyle)
            }
        },

        onMouseOut(ol, map, features) {
            clusterHover.hide(ol, map, features);
        },

        onContextTap({map, pixel, originalEvent}) {
            const vertexIds = [];
            map.forEachFeatureAtPixel(pixel, cluster => {
                cluster.get('features').forEach(f => {
                    vertexIds.push(f.getId());
                })
            })

            if (vertexIds.length) {
                const { pageX, pageY } = originalEvent;
                this.props.onVertexMenu(
                    originalEvent.target,
                    vertexIds[0],
                    { x: pageX, y: pageY }
                );
            }
        },

        onViewport(event) {
            const { product: { id: productId } } = this.props;
            const view = event.target;
            const zoom = view.getZoom();
            const pan = [...view.getCenter()];

            if (!this.currentViewport) {
                this.currentViewport = {};
            }
            this.currentViewport[productId] = { zoom, pan };

            this.saveViewportDebounce(this.props);
        },

        saveViewport(props) {
            if (this.mounted) {
                var productId = props.product.id;
                if (this.currentViewport && productId in this.currentViewport) {
                    var viewport = this.currentViewport[productId];
                    props.onUpdateViewport(productId, viewport);
                }
            }
        },

        getStyles(edgeInfo, element, ontology) {
            const { registry } = this.props;
            const calculatedStyles = registry['org.visallo.map.style']
                .reduce((styles, { canHandle, style, selectedStyle }) => {

                    /**
                     * Decide which vertices to apply style
                     *
                     * @function org.visallo.map.style~canHandle
                     * @param {object} productEdgeInfo The edge info from product->vertex
                     * @param {object} element The vertex
                     * @param {Array.<object>} element.properties The vertex properties
                     * @returns {boolean} True if extension should handle this vertex (style/selectedStyle functions will be invoked.)
                     */
                    if (this.caches.canHandle.getOrUpdate(canHandle, edgeInfo, element)) {
                        if (style) {
                            /**
                             * Return an OpenLayers [`ol.style.Style`](http://openlayers.org/en/latest/apidoc/ol.style.Style.html)
                             * object for the given element.
                             *
                             * @function org.visallo.map.style~style
                             * @param {object} productEdgeInfo The edge info from product->vertex
                             * @param {object} element The vertex
                             * @param {Array.<object>} element.properties The vertex properties
                             * @returns {ol.style.Style}
                             */
                            const normalStyle = this.caches.style.getOrUpdate(style, edgeInfo, element, ontology)
                            if (normalStyle) {
                                if (_.isArray(normalStyle)) {
                                    if (normalStyle.length) styles.normal.push(...normalStyle)
                                } else {
                                    styles.normal.push(normalStyle)
                                }
                            }
                        }

                        if (selectedStyle) {
                            const output = this.caches.selectedStyle.getOrUpdate(selectedStyle, edgeInfo, element, ontology)
                            if (output) {
                                if (_.isArray(output)) {
                                    if (output.length) styles.selected.push(...output)
                                } else {
                                    styles.selected.push(output)
                                }
                            }
                        }
                    }
                    return styles;
                }, { normal: [], selected: []})

            if (calculatedStyles.normal.length || calculatedStyles.selected.length) {
                return calculatedStyles;
            }
        },

        mapElementsToFeatures() {
            const { product } = this.props;
            const { extendedData } = product;
            if (!extendedData || !extendedData.vertices) return [];
            const { vertices, edges } = this.props.elements;
            const elementsSelectedById = { ..._.indexBy(this.props.selection.vertices), ..._.indexBy(this.props.selection.edges) };
            const elements = Object.values(vertices).concat(Object.values(edges));
            const geoLocationProperties = _.groupBy(this.props.ontologyProperties, 'dataType').geoLocation;

            const ancillaryFeatures = [];
            const clusterFeatures = [];

            elements.forEach(el => {
                const extendedDataType = extendedData[el.type === 'vertex' ? 'vertices' : 'edges'];
                const edgeInfo = extendedDataType[el.id];
                const styles = this.getStyles(edgeInfo, el, F.vertex.concept(el));

                if (extendedData.vertices[el.id] && extendedData.vertices[el.id].ancillary) {
                    ancillaryFeatures.push({
                        id: el.id,
                        element: el,
                        selected,
                        styles
                    })
                    return;
                }

                const geoLocations = geoLocationProperties && geoLocationProperties.reduce((props, { title }) => {
                        const geoProps = F.vertex.props(el, title);
                        geoProps.forEach(geoProp => {
                            const { value } = geoProp;
                            if (value) {
                                const { latitude, longitude } = value;
                                if (!isNaN(latitude) && !isNaN(longitude)) {
                                    const validCoordinates = (latitude >= -90 && latitude <= 90) && (longitude >= -180 && longitude <= 180);
                                    if (validCoordinates) {
                                        props.push([longitude, latitude])
                                    } else {
                                        console.warn('Vertex has geoLocation with invalid coordinates', value, el)
                                    }
                                }
                            }
                        })
                        return props;
                    }, []),
                    // TODO: check with edges
                    conceptType = F.vertex.prop(el, 'conceptType'),
                    selected = el.id in elementsSelectedById,
                    iconUrl = 'map/marker/image?' + $.param({
                        type: conceptType,
                        workspaceId: this.props.workspaceId,
                        scale: this.props.pixelRatio > 1 ? '2' : '1',
                    }),
                    iconUrlSelected = `${iconUrl}&selected=true`;

                clusterFeatures.push({
                    id: el.id,
                    element: el,
                    selected,
                    iconUrl,
                    iconUrlSelected,
                    iconSize: [22, 40].map(v => v * this.props.pixelRatio),
                    iconAnchor: [0.5, 1.0],
                    pixelRatio: this.props.pixelRatio,
                    styles,
                    geoLocations
                })
            })

            return { ancillaryFeatures, clusterFeatures };
        },

        legacyListeners(map) {
            this.removeEvents = [];

            _.each(map, (handler, events) => {
                var node = this.wrap;
                var func = handler;
                if (!_.isFunction(handler)) {
                    node = handler.node;
                    func = handler.handler;
                }
                this.removeEvents.push({ node, func, events });
                $(node).on(events, func);
            })
        },

        clearCaches() {
            Object.keys(this.caches).forEach(key => this.caches[key].clear())
            this.forceUpdate();
        }
    });

    return RegistryInjectorHOC(Map, [
        'org.visallo.map.style'
    ])
});
