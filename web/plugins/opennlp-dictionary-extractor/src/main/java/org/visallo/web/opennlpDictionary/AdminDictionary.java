package org.visallo.web.opennlpDictionary;

import com.google.inject.Inject;
//import com.v5analytics.webster.ParameterizedHandler;
//import com.v5analytics.webster.annotations.Handle;
import com.v5analytics.simpleorm.InMemorySimpleOrmContext;
import org.visallo.core.simpleorm.*;
import org.visallo.webster.*;
import org.visallo.webster.annotations.Handle;
import org.json.JSONArray;
import org.json.JSONObject;
import org.visallo.core.config.Configuration;
import org.visallo.core.model.user.UserRepository;
import org.visallo.core.model.workspace.WorkspaceRepository;
import org.visallo.core.simpleorm.SimpleOrmContextProvider;
import org.visallo.core.user.User;
import org.visallo.opennlpDictionary.model.DictionaryEntry;
import org.visallo.opennlpDictionary.model.DictionaryEntryRepository;

import org.visallo.core.simpleorm.SimpleOrmContextProvider;


public class    AdminDictionary implements ParameterizedHandler {
    private DictionaryEntryRepository dictionaryEntryRepository;
    private SimpleOrmContextProvider simpleOrmContextProvider;
    private InMemorySimpleOrmContext simpleOrmContext;

    @Inject
    public AdminDictionary(
            final DictionaryEntryRepository dictionaryEntryRepository,
            final UserRepository userRepository,
            final WorkspaceRepository workspaceRepository,
            final Configuration configuration) {
        this.dictionaryEntryRepository = dictionaryEntryRepository;
    }

    @Handle
    public JSONObject handle(
            User user
    ) throws Exception {

        //Iterable<DictionaryEntry> dictionary = dictionaryEntryRepository.findAll(user.getSimpleOrmContext());
        Iterable<DictionaryEntry> dictionary = dictionaryEntryRepository.findAll(simpleOrmContextProvider.getContext(user));
        JSONArray entries = new JSONArray();
        JSONObject results = new JSONObject();
        for (DictionaryEntry entry : dictionary) {
            entries.put(entry.toJson());
        }

        results.put("entries", entries);

        return results;
    }
}
