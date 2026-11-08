package vn.edu.wspm.backend.repositories;

import vn.edu.wspm.backend.dto.pattern.DictionaryResponse;

public interface DictionaryReader {
    DictionaryResponse search(String runId,String query,int page,int size);
}
