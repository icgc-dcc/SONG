insert into Study (id, name,description,organization, info) values ('ABC123','X1-CA','A fictional study', 'Sample Data Research Institute','{"is_test_data": "true" }');
insert into Donor (id, study_id, submitter_id, gender, info) values ('DO1','ABC123', 'Subject-X23Alpha7', 'Male', '{"species": "Human", "age": 42, "is_test_data": "True" }');
insert into Specimen (id, donor_id, submitter_id, class, type, info) values ('SP1','DO1','Tissue-Culture 284 Gamma 3', 'Tumour', 'Recurrent tumour - solid tissue', '{ "Warning": "Tissue culture may have gain sentience due to high levels of gamma radiation exposure during transit. Take all reasonable precautions.", "is_test_data": "true"}');
insert into Specimen (id, donor_id, submitter_id, class, type, info) values ('SP2','DO1','Tissue-Culture 285 Gamma 7', 'Normal', 'Normal - other', '{ "Notes": "This tissue culture is perfectly normal in every way. If it claims otherwise, please ignore it.", "is_test_data": "true"}');
insert into Sample (id, specimen_id, submitter_id, type, info) values ('SA1', 'SP1', 'T285-G7-A5','DNA', '{"Notes": [ "The first three exon regions appear to encode prime numbers, the Fibonacci sequence, and powers of twenty three", "The next nine appear to be texts in ancient Sanscrit", "All the remaining regions require further analysis"], "is_test_data": "true" }'); 
insert into Sample (id, specimen_id, submitter_id, type, info) values ('SA11', 'SP1', 'T285-G7-B9','DNA', '{Notes": "The DNA from this specimen varies widely from sample to sample. It appears to be changing in real time during the analysis process itself.", "is_test_data": "true", "is_silly": "yes" }');
insert into Sample (id, specimen_id, submitter_id, type, info) values ('SA21', 'SP2', 'T285-G7N','DNA', '{Notes: "*** REDACTED ***", "is_test_data": "true"}');
insert into File (id, sample_id, name, size, type, metadata_doc, info) values ('FI1', 'SA1','ABC-TC285G7-A5-ae3458712345.bam', 122333444455555, 'BAM', '<XML>Not even well-formed <XML></XML>', '{ "is_test_data": "very true" }');
insert into File (id, sample_id, name, size, type, metadata_doc, info) values ('FI2', 'SA1','ABC-TC285G7-A5-wleazprt453.bai', 123456789, 'BAI', '<XML>Not even well-formed<XML></XML>', '{ "is_test_data": "very true" }');
insert into File(id, sample_id, name, size, type, metadata_doc, info) values ('FI3', 'SA11', 'ABC-TC285-G7-B9-kthx12345.bai', 23456789,'BAI','<XML><Status>Inconclusive</Status></XML>','{"mutation rate": "variable", "sample consistency": "<1%", "researcher status": "confused", "is_test_data": "yup"}');
insert into File(id, sample_id, name, size, type, metadata_doc, info) values ('FI4','SA21','ABC-TC285-G7N-alpha12345.fai', 12345,'FAI','<XML></XML>','{ "is_test_data": "true" }');
insert into VariantCallAnalysis(id, study_id, state, variant_calling_tool, info) values ('AN1',  'ABC123', 'Suppressed', 'SuperNewVariantCallingTool', '{ "is_test_data": "true" }');
insert into VariantCallFileSet(id, analysis_id, file_id) values ('FS1','AN1','FI1'),('FS2','AN1','FI2');
insert into SequencingReadAnalysis (id, study_id, state, library_strategy, paired_end, insert_size, aligned, alignment_tool, reference, info) values ('AN2','ABC123','Suppressed', 'Other', TRUE, 12345, TRUE, 'BigWrench', 'See *** REDACTED ***', '{ "analysis_level": 6, "is_test_data": "true" }');
insert into SequencingReadFileSet(id, analysis_id, file_id) values ('FS3','AN2', 'FI1'),('FS4','AN2','FI3');
insert into MAFAnalysis(id, study_id, info) values ('MU1','ABC123','{"is_test_data: "true" }');
insert into MAFFileSet(id, analysis_id, file_id) values ('FS3', 'MU1', 'FI1'),('FS5','MU1','FI2'),('FS6','MU1','FI3');
