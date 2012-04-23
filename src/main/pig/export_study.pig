register 'elephant-bird-2.2.0.jar';

SET pig.logfile testpilot_export_study.log;
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
pairs = LOAD '/bagheera/$study/*' USING com.twitter.elephantbird.pig.load.SequenceFileLoader (
    '-c com.twitter.elephantbird.pig.util.TextConverter', 
    '-c com.twitter.elephantbird.pig.util.TextConverter'
) AS (key: chararray, json: chararray);

STORE pairs INTO '$study-dump';