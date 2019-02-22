package org.visallo.opennlpDictionary.model;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;
import com.v5analytics.simpleorm.InMemorySimpleOrmSession;
import com.v5analytics.simpleorm.SimpleOrmContext;
import com.v5analytics.simpleorm.SimpleOrmSession;
import com.v5analytics.simpleorm.InMemorySimpleOrmContext;
import org.visallo.core.simpleorm.SimpleOrmContextProvider;
import org.visallo.core.user.User;

public class DictionaryEntryRepository {
    private static final String VISIBILITY_STRING = "";
    private final SimpleOrmSession simpleOrmSession;
    //private final InMemorySimpleOrmSession simpleOrmSession;
    private final SimpleOrmContextProvider simpleOrmContextProvider;

    @Inject
    public DictionaryEntryRepository(
            SimpleOrmSession simpleOrmSession,
            SimpleOrmContextProvider simpleOrmContextProvider
    ) {
        this.simpleOrmSession = simpleOrmSession;
        this.simpleOrmContextProvider = simpleOrmContextProvider;
    }

    public Iterable<DictionaryEntry> findAll(SimpleOrmContext simpleOrmContext) {
        return this.simpleOrmSession.findAll(DictionaryEntry.class, simpleOrmContext);
    }

    public Iterable<DictionaryEntry> findByConcept(final String concept, User user) {
        //Iterable<DictionaryEntry> rows = findAll(user.getSimpleOrmContext());
        Iterable<DictionaryEntry> rows = findAll(simpleOrmContextProvider.getContext(user));
        return Iterables.filter(rows, new Predicate<DictionaryEntry>() {
            @Override
            public boolean apply(DictionaryEntry dictionaryEntry) {
                return dictionaryEntry.getConcept().equals(concept);
            }
        });
    }

    public void delete(String id, User user) {
        //this.simpleOrmSession.delete(DictionaryEntry.class, id, user.getSimpleOrmContext());
        this.simpleOrmSession.delete(DictionaryEntry.class, id, simpleOrmContextProvider.getContext(user));
    }

    public DictionaryEntry createNew(String tokens, String concept) {
        return createNew(tokens, concept, null);
    }

    public DictionaryEntry createNew(String tokens, String concept, String resolvedName) {
        return new DictionaryEntry(
                tokens,
                concept,
                resolvedName
        );
    }

    public DictionaryEntry saveNew(String tokens, String concept, String resolvedName, User user) {
        DictionaryEntry entry = createNew(tokens, concept, resolvedName);
        //this.simpleOrmSession.save(entry, VISIBILITY_STRING, user.getSimpleOrmContext());
        this.simpleOrmSession.save(entry, VISIBILITY_STRING, simpleOrmContextProvider.getContext(user));
        return entry;
    }

    public DictionaryEntry saveNew(String tokens, String concept, User user) {
        DictionaryEntry entry = createNew(tokens, concept);
        this.simpleOrmSession.save(entry, VISIBILITY_STRING, simpleOrmContextProvider.getContext(user));
        return entry;
    }
}
