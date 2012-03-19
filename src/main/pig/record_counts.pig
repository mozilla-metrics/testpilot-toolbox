register 'elephant-bird-2.1.8.jar';

%declare SEQFILE_LOADER 'com.twitter.elephantbird.pig.load.SequenceFileLoader';
%declare TEXT_CONVERTER 'com.twitter.elephantbird.pig.util.TextConverter';

/* 
Basic example to demonstrate reading testpilot data using pig with Elephant Bird's SequenceFileLoader 
 - Each study has dated subfolders which we want to add so use wildcard to match all
*/
pairs = LOAD '/bagheera/testpilot_6/*' USING com.twitter.elephantbird.pig.load.SequenceFileLoader (
    '-c com.twitter.elephantbird.pig.util.TextConverter', 
    '-c com.twitter.elephantbird.pig.util.TextConverter'
) AS (key: chararray, json: chararray);

grouped = GROUP pairs all;
doc_count = FOREACH grouped GENERATE COUNT(pairs);
DUMP doc_count;