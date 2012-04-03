register 'elephant-bird-2.1.8.jar';

SET pig.logfile tespilot_record_counts.log;
/*
SET default_parallel 8;
SET pig.tmpfilecompression true;
SET pig.tmpfilecompression.codec lzo;
SET mapred.compress.map.output true;
SET mapred.map.output.compression.codec org.apache.hadoop.io.compress.SnappyCodec;
*/

/* 
Basic example to demonstrate reading testpilot data using pig with Elephant Bird's SequenceFileLoader 
 - Each study has dated subfolders which we want to add so use wildcard to match all
*/
pairs = LOAD '/bagheera/testpilot_6/*' USING com.twitter.elephantbird.pig.load.SequenceFileLoader(
    '-c com.twitter.elephantbird.pig.util.TextConverter', 
    '-c com.twitter.elephantbird.pig.util.TextConverter'
) AS (key: chararray, json: chararray);

grouped = GROUP pairs all;
doc_count = FOREACH grouped GENERATE COUNT(pairs);
DUMP doc_count;