define([
    'flight/lib/component',
    'configuration/admin/utils/withFormHelpers',
    'hbs!org/visallo/web/devTools/templates/ontology-edit-property',
    'util/formatters',
    'util/withDataRequest'
], function(
    defineComponent,
    withFormHelpers,
    template,
    F,
    withDataRequest) {
    'use strict';

    return defineComponent(OntologyEditProperty, withDataRequest, withFormHelpers);

    function OntologyEditProperty() {

        this.defaultAttrs({
            propertySelector: '.property-container',
            buttonSelector: '.btn-primary'
        });

        this.after('initialize', function() {
            var self = this;

            this.on('click', {
                buttonSelector: this.onSave
            });

            this.on('propertyselected', this.onPropertySelected);

            this.$node.html(template({}));

            Promise.all([
                Promise.require('util/ontology/propertySelect'),
                Promise.require('util/messages'),
                this.dataRequest('ontology', 'properties'),
                this.dataRequest('ontology', 'concepts')
            ]).done(function(results) {
                var FieldSelection = results.shift(),
                    i18n = results.shift(),
                    properties = results.shift();
                self.concepts = results.shift();

                FieldSelection.attachTo(self.select('propertySelector'), {
                    properties: properties.list,
                    showAdminProperties: true,
                    placeholder: i18n('property.form.field.selection.placeholder')
                });
            })
        });

        this.onSave = function() {
            var self = this;

            this.handleSubmitButton(
                this.select('buttonSelector'),
                this.dataRequest('io-visallo-web-devTools', 'ontologyEditProperty', {
                    property: this.currentProperty,
                    displayName: this.$node.find('.displayName').val(),
                    dataType: this.$node.find('.dataType').val(),
                    displayType: this.$node.find('.displayType').val(),
                    addable: this.$node.find('.addable').is(':checked'),
                    sortable: this.$node.find('.sortable').is(':checked'),
                    searchable: this.$node.find('.searchable').is(':checked'),
                    userVisible: this.$node.find('.userVisible').is(':checked'),
                    updateable: this.$node.find('.updateable').is(':checked'),
                    deleteable: this.$node.find('.deleteable').is(':checked'),
                    displayFormula: this.$node.find('.displayFormula').val(),
                    validationFormula: this.$node.find('.validationFormula').val(),
                    possibleValues: this.$node.find('.possibleValues').val(),
                    intents: this.$node.find('.intents').val().split(/[\n\s,]+/),
                    domains: this.$node.find('.domains').val().split(/[\n\s,]+/),
                    dependentPropertyIris: this.$node.find('.dependentPropertyIris')
                        .val().split(/[\n\s,]+/)
                })
                    .then(function() {
                        self.showSuccess('Saved, refresh to see changes');
                    })
                    .catch(function() {
                        self.showError();
                    })

            )
        };

        this.onPropertySelected = function(event, data) {
            var self = this;

            if (data.property) {
                this.currentProperty = data.property.title;
                this.$node.find('.btn-primary').removeAttr('disabled');

                data.property.userVisible = data.property.userVisible !== false;
                data.property.searchable = data.property.searchable !== false;
                data.property.addable = data.property.addable !== false;
                data.property.sortable = data.property.sortable !== false;
                data.property.deleteable = data.property.deleteable !== false;
                data.property.updateable = data.property.updateable !== false;

                this.$node.find('*').not('.property-container *').val('').removeAttr('checked');
                _.each(data.property, function(value, key) {
                    if (key === 'dependentPropertyIris') {
                        value = value.join('\n');
                    }
                    if (key === 'possibleValues' && value) {
                        value = JSON.stringify(value, null, 2);
                    }
                    if (key === 'intents') {
                        value = value.join('\n');
                    }
                    self.updateFieldValue(key, value)
                });

                var domains = [];
                _.each(self.concepts.byId, function(value, key) {
                    _.each(value.properties, function(prop) {
                        if (prop === data.property.title) {
                            domains.push(key);
                        }
                    });
                });
                self.updateFieldValue('domains', domains.join('\n'));
            } else {
                this.$node.find('.btn-primary').attr('disabled', true);
            }
        };

        this.updateFieldValue = function(field, value) {
            var $field = this.$node.find('.' + field),
                type = $field.prop('type');

            if (!$field.length) {
                return;
            }

            switch (type) {
                case 'text':
                case 'textarea':
                    $field.val(value);
                    break;
                case 'checkbox':
                    $field.prop('checked', value);
                    break;
                default:
                    console.error('Unhandled type', type);
            }
        }
    }
});
