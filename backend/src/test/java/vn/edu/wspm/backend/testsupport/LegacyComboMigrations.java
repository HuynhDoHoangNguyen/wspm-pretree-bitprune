package vn.edu.wspm.backend.testsupport;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.flywaydb.core.api.ResourceProvider;
import org.flywaydb.core.api.resource.LoadableResource;

/** Isolated historical TV2 protocol tests. Reads exact original SQL; never adds drafts to runtime scan. */
public final class LegacyComboMigrations {
    private LegacyComboMigrations(){}
    public static ResourceProvider provider(boolean writer){
        var resources=new ArrayList<LoadableResource>();
        for(String name:List.of("V1__initial_schema.sql","V2__complete_laptop_combo_schema.sql")){
            try(var in=LegacyComboMigrations.class.getResourceAsStream("/db/migration/"+name)){
                if(in==null)throw new IllegalStateException("MISSING_LEGACY_BASE_SQL");
                resources.add(resource(name,"classpath:db/migration/"+name,new String(in.readAllBytes(),StandardCharsets.UTF_8)));
            }catch(IOException e){throw new UncheckedIOException(e);}
        }
        Path base=Files.isDirectory(Path.of("backend/migration-review"))?Path.of("backend/migration-review"):Path.of("migration-review");
        var folders=writer?List.of("TV2-INT-08F","INT-09_10"):List.of("TV2-INT-08F");
        for(String folder:folders){
            try(var files=Files.list(base.resolve(folder))){
                for(Path p:files.filter(p->p.getFileName().toString().endsWith(".sql")).toList())resources.add(resource(p.getFileName().toString(),p.toAbsolutePath().toString(),Files.readString(p)));
            }catch(IOException e){throw new UncheckedIOException(e);}
        }
        return new ResourceProvider(){
            public LoadableResource getResource(String name){return resources.stream().filter(r->r.getFilename().equals(name)||r.getAbsolutePath().equals(name)).findFirst().orElse(null);}
            public Collection<LoadableResource> getResources(String prefix,String[] suffixes){return resources.stream().filter(r->r.getFilename().startsWith(prefix)&&Arrays.stream(suffixes).anyMatch(s->r.getFilename().endsWith(s))).toList();}
        };
    }
    private static LoadableResource resource(String name,String path,String text){return new LoadableResource(){
        public Reader read(){return new StringReader(text);}
        public String getAbsolutePath(){return path;}
        public String getAbsolutePathOnDisk(){return path;}
        public String getFilename(){return name;}
        public String getRelativePath(){return name;}
    };}
}
