from pathlib import Path
import json,re
import argparse
parser=argparse.ArgumentParser()
parser.add_argument('--check', action='store_true')
args=parser.parse_args()
root=Path(__file__).resolve().parents[1]
def output(name, content):
    path=root/name
    if args.check:
        if not path.exists() or path.read_text()!=content:
            raise SystemExit('Outdated error catalog: '+name)
    else:
        path.write_text(content)

source=(root/'src/main/java/com/company/integration_catalog/exception/ErrorCode.java').read_text()
messages=dict(re.findall(r'^    ([A-Z][A-Z_]+)\("([^"\n]*)"\)',source,re.M))
entries={code:{'code':code,'message':message,'occurrences':[]} for code,message in messages.items()}
all_api=['* /api/v1/**']
schemas=['POST /api/v1/integrations/{id}/mappings/schemas','POST /api/v1/integrations/{id}/mappings/schemas/upload']
fields=['POST /api/v1/integrations/{id}/mappings/fields']
jobs=['POST /api/v1/integrations/{id}/mappings/ai/jobs']
append=['POST /api/v1/integrations/{id}/mappings/fields/append']
status=['GET /api/v1/integrations/{id}/mappings/ai/jobs/{jobId}']
result=['GET /api/v1/integrations/{id}/mappings/ai/jobs/{jobId}/result']
capability=['GET /api/v1/integrations/{id}/mappings/ai/capabilities']
complete=['PATCH /api/v1/integrations/{id}/complete']
users=['POST /api/v1/users']
integration_write=['POST /api/v1/integrations','PUT /api/v1/integrations/{id}']
config=['POST /api/v1/integrations/{id}/configs']
def add(codes,kind,statuses,endpoints,**extra):
    for code in codes.split():
        assert code in entries,code
        entries[code]['occurrences'].append({'type':kind,'httpStatuses':statuses,'endpoints':endpoints,**extra})
for status_code,code in [(400,'BAD_REQUEST'),(400,'VALIDATION_FAILED'),(400,'INVALID_REQUEST_BODY'),(400,'INVALID_PARAMETER'),(400,'MISSING_REQUEST_HEADER'),(400,'MISSING_REQUEST_PARAMETER'),(405,'METHOD_NOT_ALLOWED'),(406,'NOT_ACCEPTABLE'),(415,'UNSUPPORTED_MEDIA_TYPE'),(404,'RESOURCE_NOT_FOUND'),(409,'CONCURRENT_MODIFICATION'),(409,'DATA_CONFLICT'),(409,'CONFLICT'),(500,'INTERNAL_ERROR'),(503,'SERVICE_UNAVAILABLE')]:
    add(code,'http',[status_code],all_api)
add('AUTH_REQUIRED SESSION_INVALID','http',[401],all_api+['GET /swagger-ui/**','GET /v3/api-docs/**'])
add('INVALID_CREDENTIALS','http',[401],['POST /api/v1/auth/login'])
add('ACCESS_DENIED','http',[403],all_api+['GET /swagger-ui/**','GET /v3/api-docs/**'])
add('CSRF_MISSING CSRF_INVALID','http',[403],['POST|PUT|PATCH|DELETE /api/v1/**'])
add('ACCOUNT_VERIFICATION_UNAVAILABLE','http',[503],all_api)
add('RATE_LIMITED','http',[429],['POST /api/v1/auth/**'])
add('MISSING_UPLOAD_PART','http',[400],[schemas[1]])
add('INTEGRATION_NOT_FOUND','http',[404],['* /api/v1/integrations/{id}','* /api/v1/integrations/{id}/**'])
add('SYSTEM_NOT_FOUND','http',[404],['GET|PUT|DELETE /api/v1/systems/{id}','PATCH /api/v1/systems/{id}/active']+integration_write)
add('SCHEMA_NOT_FOUND','http',[404],['GET /api/v1/integrations/{id}/mappings/schemas/{schemaId}/sample']+fields+jobs+append)
add('SAMPLE_FILE_NOT_FOUND','http',[404],['GET /api/v1/integrations/{id}/mappings/schemas/{schemaId}/sample'])
add('AI_JOB_NOT_FOUND','http',[404],status+result+append)
add('ACCOUNT_NOT_FOUND','http',[404],['GET /api/v1/auth/me','POST /api/v1/auth/password/change','POST /api/v1/auth/email/change','POST|PUT /api/v1/users/{id}/**'])
add('CONFIG_NOT_FOUND','http',[404],['GET /api/v1/integrations/{id}/configs/{environment}/{target}/credentials'])
add('USERNAME_TAKEN','http',[409],users)
add('EMAIL_UNAVAILABLE','http',[409],users+['POST /api/v1/auth/email/change'])
add('INVITATION_NOT_PENDING','http',[409],['POST /api/v1/users/{id}/invitations/resend'])
add('INVITATION_RATE_LIMITED','http',[429],['POST /api/v1/users/{id}/invitations/resend'])
add('ACCOUNT_EMAIL_REQUIRED LAST_ADMIN_RESET_RESTRICTED','http',[409],['POST /api/v1/users/{id}/password-reset'])
add('PASSWORD_RESET_RATE_LIMITED','http',[429],['POST /api/v1/users/{id}/password-reset'])
add('EMAIL_CHANGE_RATE_LIMITED','http',[429],['POST /api/v1/auth/email/change'])
add('LAST_ADMIN_REQUIRED','http',[409],['PUT /api/v1/users/{id}/role','PUT /api/v1/users/{id}/active'])
add('EMAIL_VERIFICATION_REQUIRED','http',[409],['PUT /api/v1/users/{id}/active'])
add('ACCOUNT_UNAVAILABLE','http',[403],['POST /api/v1/auth/password/change','POST /api/v1/auth/email/change'])
add('CURRENT_PASSWORD_INVALID','http',[400],['POST /api/v1/auth/password/change','POST /api/v1/auth/email/change'])
add('PASSWORD_REUSE','http',[400],['POST /api/v1/auth/password/change','POST /api/v1/auth/password-reset/confirm'])
add('ACCOUNT_LINK_INVALID','http',[400],['POST /api/v1/auth/invitations/accept','POST /api/v1/auth/password-reset/confirm','POST /api/v1/auth/email/confirm'])
add('CREDENTIAL_PERMISSION_REQUIRED','http',[403],config+['GET /api/v1/integrations/{id}/configs/{environment}/{target}/credentials'])
add('SYSTEM_IN_USE','http',[409],['DELETE /api/v1/systems/{id}'])
add('INACTIVE_SYSTEM_SELECTION','http',[409],integration_write)
add('DUPLICATE_ENVIRONMENT_CONFIG CREDENTIALS_EMPTY CREDENTIALS_REQUIRED','http',[400],config)
add('INTEGRATION_INCOMPLETE','http',[409],complete)
completion_codes='REVIEW_REQUIRED BASIC_INFORMATION_REQUIRED INVALID_MIDDLEWARE_SELECTION DUPLICATE_SCHEMA_TYPE REQUIRED_SCHEMA_MISSING INVALID_MAPPING_CONNECTION ENVIRONMENT_CONFIG_REQUIRED INVALID_ENVIRONMENT_CONFIG MIDDLEWARE_CONFIG_NOT_ALLOWED DESTINATION_CONFIG_REQUIRED MIDDLEWARE_CONFIG_REQUIRED DUPLICATE_ENVIRONMENT_CONFIG'
add(completion_codes,'violation',[409],complete)
add('REQUIRED INVALID_EMAIL INVALID_LENGTH INVALID_FORMAT INVALID_VALUE MIN_VALUE MAX_VALUE POSITIVE_REQUIRED NON_NEGATIVE_REQUIRED INVALID_MIDDLEWARE_SELECTION','violation',[400],all_api)
add('INVALID_SCHEMA_TYPE FORMAT_MISMATCH INVALID_CONTENT EMPTY_CONTENT INVALID_XML XML_COMPLEXITY_LIMIT INVALID_IDOC INVALID_ENCODING','http',[400],schemas)
add('INVALID_SCHEMA_TYPE FORMAT_MISMATCH INVALID_CONTENT EMPTY_CONTENT INVALID_XML XML_COMPLEXITY_LIMIT INVALID_IDOC INVALID_ENCODING INVALID_SCHEMA','http',[422],jobs+append)
add('INVALID_SCHEMA_TYPE FORMAT_MISMATCH INVALID_CONTENT EMPTY_CONTENT INVALID_XML XML_COMPLEXITY_LIMIT INVALID_IDOC INVALID_ENCODING INVALID_SCHEMA EMPTY_SCHEMA FILE_REQUIRED MAPPING_NOT_SUPPORTED INVALID_CSV_OPTIONS INVALID_CSV INVALID_EXCEL PAYLOAD_TOO_LARGE FIELD_PATH_NOT_FOUND SAMPLE_REQUIRED INVALID_JSON_SCHEMA INPUT_TOO_COMPLEX TOO_MANY_FIELDS','violation',[409],complete)
add('FORMAT_REQUIRED INVALID_FILE INVALID_FILE_NAME UNSUPPORTED_FILE_EXTENSION','http',[400],[schemas[1]])
add('INVALID_JSON FILE_REQUIRED MAPPING_NOT_SUPPORTED INVALID_CSV_OPTIONS INVALID_CSV INVALID_EXCEL','http',[400],schemas)
add('MAPPING_NOT_SUPPORTED FIELD_PATH_NOT_FOUND','http',[400],fields)
add('SAMPLE_REQUIRED INVALID_JSON_SCHEMA INPUT_TOO_COMPLEX TOO_MANY_FIELDS','http',[422],schemas+fields+jobs+append)
add('FIELD_PATH_NOT_FOUND','http',[422],jobs+append)
add('PAYLOAD_TOO_LARGE','http',[413],schemas)
add('PAYLOAD_TOO_LARGE','http',[422],jobs+append)
add('SCHEMA_MAPPING_CONFLICT','http',[409],schemas)
add('INVALID_IDEMPOTENCY_KEY','http',[400],jobs+append)
add('INVALID_SCHEMA_PAIR AI_FORMAT_NOT_SUPPORTED','http',[422],jobs+append)
add('INVALID_SUGGESTION','http',[422],append)
add('AI_INPUT_TOO_LARGE AI_CONTEXT_LIMIT','http',[413],jobs)
add('AI_QUEUE_FULL','http',[429],jobs)
add('CONTEXT_CHANGED IDEMPOTENCY_CONFLICT','http',[409],jobs+append)
add('MAPPING_CONFLICT','http',[409],append)
add('AI_DISABLED AI_PROVIDER_NOT_SUPPORTED AI_API_KEY_MISSING AI_CONFIGURATION_INVALID','http',[503],jobs)
add('AI_RESULT_EXPIRED','http',[410],result+append)
add('AI_RESULT_NOT_READY','http',[409],result+append)
failed='AI_API_KEY_MISSING AI_CONFIGURATION_INVALID AI_AUTHENTICATION_FAILED AI_MODEL_NOT_AVAILABLE AI_RATE_LIMITED AI_TIMEOUT AI_UNAVAILABLE AI_INVALID_OUTPUT AI_CONTENT_BLOCKED AI_OUTPUT_TRUNCATED AI_INTERRUPTED AI_CONFIGURATION_CHANGED AI_INTERNAL_ERROR AI_MODEL_NOT_INSTALLED AI_CLOUD_MODEL_FORBIDDEN'
add(failed,'job',[200],status,jobStatus='FAILED')
add('AI_RESULT_EXPIRED','job',[200],status,jobStatus='EXPIRED')
add('AI_DISABLED AI_PROVIDER_NOT_SUPPORTED AI_API_KEY_MISSING AI_CONFIGURATION_INVALID AI_AUTHENTICATION_FAILED AI_MODEL_NOT_AVAILABLE AI_RATE_LIMITED AI_TIMEOUT AI_UNAVAILABLE AI_INVALID_OUTPUT AI_MODEL_NOT_INSTALLED AI_CLOUD_MODEL_FORBIDDEN','capability',[200],capability)
assert all(e['occurrences'] for e in entries.values()), [code for code,e in entries.items() if not e['occurrences']]
request_id='8e602015-ea22-4a62-bc33-cda40345b09d'
def error_body(status,code):
    reasons={400:'Bad Request',401:'Unauthorized',403:'Forbidden',404:'Not Found',405:'Method Not Allowed',406:'Not Acceptable',409:'Conflict',410:'Gone',413:'Payload Too Large',415:'Unsupported Media Type',422:'Unprocessable Entity',429:'Too Many Requests',500:'Internal Server Error',503:'Service Unavailable'}
    return {'timestamp':'2026-10-31T18:00:00Z','status':status,'error':reasons[status],'code':code,'message':messages[code],'path':'/api/v1/example','requestId':request_id,'details':[],'violations':[],'params':{}}
for code,entry in entries.items():
    examples=[]
    for occurrence in entry['occurrences']:
        kind=occurrence['type'];http=occurrence['httpStatuses'][0]
        headers={'X-Request-ID':request_id}
        if kind=='http':
            body=error_body(http,code)
            field_errors={'USERNAME_TAKEN':'username','EMAIL_UNAVAILABLE':'email','CURRENT_PASSWORD_INVALID':'currentPassword','PASSWORD_REUSE':'newPassword'}
            if code in field_errors: body['violations']=[{'field':field_errors[code],'code':code,'params':{}}]
            if code=='SYSTEM_IN_USE': body['params']={'usageCount':5}
            if code=='MISSING_REQUEST_HEADER': body['params']={'header':'Idempotency-Key'}
            if code=='MISSING_REQUEST_PARAMETER': body['params']={'parameter':'requiredParameter'}
            if code=='MISSING_UPLOAD_PART': body['params']={'part':'file'}
            if code=='INVALID_PARAMETER': body['violations']=[{'field':'id','code':'INVALID_VALUE','params':{}}]
            if code=='PAYLOAD_TOO_LARGE' and http==413: body['params']={'maxBytes':1048576}
            if code=='TOO_MANY_FIELDS': body['params']={'maxFields':100}
            if http==429:headers['Retry-After']='5' if code=='AI_QUEUE_FULL' else '60'
        elif kind=='violation':
            body=error_body(http,'INTEGRATION_INCOMPLETE' if http==409 else 'VALIDATION_FAILED')
            constraint_fields={'REQUIRED':'email','INVALID_EMAIL':'email','INVALID_LENGTH':'newPassword','INVALID_FORMAT':'username','INVALID_VALUE':'confirmed','MIN_VALUE':'page','MAX_VALUE':'size','POSITIVE_REQUIRED':'[0].timeoutMs','NON_NEGATIVE_REQUIRED':'[0].retryCount','INVALID_MIDDLEWARE_SELECTION':'middlewareSystemId'}
            parameters={}
            if code=='INVALID_LENGTH': parameters={'min':12,'max':128}
            if code=='MIN_VALUE': parameters={'min':0}
            if code=='MAX_VALUE': parameters={'max':100}
            if code in {'REQUIRED_SCHEMA_MISSING','DUPLICATE_SCHEMA_TYPE','INVALID_SCHEMA_TYPE'}: parameters={'schemaType':'SOURCE_TO_DEST_REQUEST'}
            if code in {'DESTINATION_CONFIG_REQUIRED','MIDDLEWARE_CONFIG_REQUIRED','MIDDLEWARE_CONFIG_NOT_ALLOWED'}: parameters={'environment':'DEV'}
            if code=='INVALID_MAPPING_CONNECTION': parameters={'mappingId':1}
            body['violations']=[{'field':None if http==409 else constraint_fields.get(code,'fieldName'),'code':code,'params':parameters}]
            if http==409:body['details']=[messages[code]]
        elif kind=='job':
            body={'jobId':'example-job-id','status':occurrence['jobStatus'],'createdAt':'2026-10-31T18:00:00Z','expiresAt':'2026-11-01T18:00:00Z','errorCode':code,'message':messages[code],'resultUrl':None,'pollAfterMs':None}
        else:
            provider='gemini' if code in {'AI_API_KEY_MISSING','AI_AUTHENTICATION_FAILED','AI_MODEL_NOT_AVAILABLE','AI_RATE_LIMITED','AI_CONFIGURATION_INVALID'} else 'ollama'
            if code=='AI_PROVIDER_NOT_SUPPORTED': provider='unsupported'
            body={'enabled':code!='AI_DISABLED','available':False,'provider':provider,'model':'configured-model','supportedFormats':['JSON','XML','IDOC'],'maxFieldsPerSide':100,'code':code}
        endpoint=occurrence['endpoints'][0]
        if 'path' in body:body['path']=endpoint.split(' ',1)[1].replace('{id}','1').replace('{jobId}','example-job-id').replace('{schemaId}','1').replace('**','example').replace('*','example')
        examples.append({'type':kind,'httpStatus':http,'headers':headers,'body':body})
    entry['examples']=examples
catalog={'schemaVersion':1,'messageLanguage':'en','notes':['Endpoint patterns describe families; | separates allowed methods and * matches routes.','A violation code is nested in violations; the envelope code is VALIDATION_FAILED or INTEGRATION_INCOMPLETE.','Job failures and capability availability are successful HTTP 200 representations, not HTTP errors.','Retry-After is a positive integer in seconds; AI queue delay is advisory.','All application error bodies carry requestId; successful job/capability representations carry X-Request-ID in the header.'], 'codes':list(entries.values()),'availabilitySuccessCode':'READY','mappingDiagnosticCodes':['AMBIGUOUS','NO_COMPATIBLE_FIELD','TYPE_MISMATCH','INSUFFICIENT_CONTEXT','TRANSFORMATION_REQUIRED','NO_CONFIDENT_MATCH']}
output('docs/error-catalog.json', json.dumps(catalog,ensure_ascii=False,indent=2)+'\n')
rows=['| Code | Context / HTTP | Endpoints | English fallback |','|---|---|---|---|']
for code,entry in entries.items():
    contexts=[]; endpoints=[]
    for item in entry['occurrences']:
        contexts.append(item['type']+' '+','.join(str(s) for s in item['httpStatuses']))
        endpoints += [e for e in item['endpoints'] if e not in endpoints]
    rows.append('| `'+code+'` | '+'; '.join(contexts)+' | '+'<br>'.join('`'+e.replace('|',' / ')+'`' for e in endpoints)+' | '+entry['message']+' |')
output('docs/error-code-index.md', '# Error code index\n\nThis index lists every supported error code. Contexts distinguish top-level HTTP errors, nested violations, asynchronous job states and capability availability.\n\nEndpoint patterns describe route families; the applicable checks depend on the operation and payload. Examples for every code and context are in [the JSON catalog](error-catalog.json). The shared response contract is in [API errors](error-codes.md).\n\n'+'\n'.join(rows)+'\n')
print('Error catalog checked for' if args.check else 'Generated catalog and endpoint index for', len(entries), 'error codes.')
