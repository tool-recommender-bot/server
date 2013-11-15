--
-- Updating hibernate sequence start value
--
SELECT pg_catalog.setval('hibernate_sequence', 9, false);

--
-- Extension data for registration purpose
--

Insert INTO scim_extension VALUES (6, 'urn:scim:schemas:osiam:1.0:Registration');

INSERT INTO scim_extension_field VALUES (7, false, 'activationToken', 'STRING', 6);
INSERT INTO scim_extension_field VALUES (8, false, 'oneTimePassword', 'STRING', 6);