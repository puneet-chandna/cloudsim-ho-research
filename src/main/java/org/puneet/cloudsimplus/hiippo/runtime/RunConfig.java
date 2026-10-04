package org.puneet.cloudsimplus.hiippo.runtime;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public record RunConfig(Profile profile,long masterSeed,String logLevel,Path outputRoot) {
    public RunConfig {
        Objects.requireNonNull(profile); Objects.requireNonNull(outputRoot);
        if(!Set.of("INFO","DEBUG").contains(logLevel)) throw new IllegalArgumentException("log.level must be INFO or DEBUG");
    }
    public static RunConfig parse(String[] args) throws IOException {
        CaseExecutor.workers();
        var options=new HashMap<String,String>();
        for(int i=0;i<args.length;i++) {
            String key=args[i];
            if(!Set.of("--profile","--config","--output-dir","--debug").contains(key)) throw new IllegalArgumentException("Unknown or conflicting option: "+key);
            if(options.containsKey(key)) throw new IllegalArgumentException("Duplicate option: "+key);
            String value="true";
            if(!key.equals("--debug")) {
                if(++i==args.length || args[i].startsWith("--") || args[i].isBlank()) throw new IllegalArgumentException("Missing value for "+key);
                value=args[i];
            }
            options.put(key,value);
        }
        if(!options.containsKey("--profile")) throw new IllegalArgumentException("--profile is required");
        var profile=Profile.valueOf(options.get("--profile"));
        var properties=new Properties() {
            @Override public synchronized Object put(Object key,Object value) {
                if(containsKey(key)) throw new IllegalArgumentException("Duplicate property: "+key);
                if(!Set.of("master.seed","log.level").contains(key)) throw new IllegalArgumentException("Unknown property: "+key);
                return super.put(key,value);
            }
        };
        if(options.containsKey("--config")) {
            try(var reader=Files.newBufferedReader(Path.of(options.get("--config")),StandardCharsets.UTF_8)) { properties.load(reader); }
            catch(java.nio.charset.CharacterCodingException e) { throw new IllegalArgumentException("Config must contain valid UTF-8",e); }
        }
        String seed=properties.getProperty("master.seed","123456");
        if(!seed.matches("[+-]?[0-9]+")) throw new IllegalArgumentException("master.seed must be signed decimal");
        String level=properties.getProperty("log.level","INFO");
        if(!Set.of("INFO","DEBUG").contains(level)) throw new IllegalArgumentException("log.level must be INFO or DEBUG");
        return new RunConfig(profile,Long.parseLong(seed),options.containsKey("--debug")?"DEBUG":level,Path.of(options.getOrDefault("--output-dir","results")));
    }
    public SortedMap<String,String> effective() throws IOException {
        var p=new Properties();
        try(var stream=RunConfig.class.getResourceAsStream("/protocol.properties")) {
            if(stream==null) throw new IOException("Missing frozen protocol constants");
            p.load(new InputStreamReader(stream,StandardCharsets.UTF_8));
        }
        var map=new TreeMap<String,String>(); p.forEach((k,v)->map.put(k.toString(),v.toString()));
        map.put("profile",profile.name()); map.put("master.seed",Long.toString(masterSeed)); map.put("log.level",logLevel);
        map.put("population",Integer.toString(profile.population)); map.put("iterations",Integer.toString(profile.iterations));
        map.put("replications",Integer.toString(profile.replications)); map.put("evaluation.budget",Integer.toString(profile.budget()));
        map.put("scenarios",String.join(",",profile.scenarios)); map.put("expected.cases",Integer.toString(profile.cases().size()));
        return Collections.unmodifiableSortedMap(map);
    }
}
