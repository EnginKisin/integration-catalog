#!/usr/bin/env python3
import http.cookiejar
import argparse
import json
import os
from pathlib import Path
import shlex
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

def run_smoke(source_format='JSON', target_format='JSON'):
    values = {}
    env_file = Path(__file__).resolve().parents[1] / '.env'
    if env_file.exists():
        for line in env_file.read_text().splitlines():
            line = line.strip()
            if line and not line.startswith('#') and '=' in line:
                key, value = line.split('=', 1)
                parts = shlex.split(value, comments=True)
                values[key.strip()] = parts[0] if parts else ''
    values.update(os.environ)
    base = values.get('CATALOG_API_BASE_URL', 'http://127.0.0.1:' + values.get('APP_PORT', '8080')) + '/api/v1'
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
    csrf = None

    def call(method, path, data=None, form=False, key=None):
        headers = {}
        if csrf and method != 'GET':
            headers[csrf['headerName']] = csrf['token']
        if key:
            headers['Idempotency-Key'] = key
        body = None
        if data is not None:
            body = (urllib.parse.urlencode(data) if form else json.dumps(data)).encode()
            headers['Content-Type'] = 'application/x-www-form-urlencoded' if form else 'application/json'
        try:
            with opener.open(urllib.request.Request(base + path, body, headers, method=method), timeout=20) as response:
                content = response.read()
                return json.loads(content) if content else None
        except urllib.error.HTTPError as error:
            raise RuntimeError(f'{method} {path}: HTTP {error.code}: {error.read().decode()}') from None

    csrf = call('GET', '/auth/csrf')
    call('POST', '/auth/login', {'username': values.get('CATALOG_ADMIN_USERNAME', ''),
                               'password': values.get('CATALOG_ADMIN_PASSWORD', '')}, form=True)
    csrf = call('GET', '/auth/csrf')
    systems = call('GET', '/systems')
    created_systems = []
    integration_id = None
    try:
        active = [item for item in systems if item['active']]
        while len(active) < 2:
            system = call('POST', '/systems', {'name': 'AI smoke ' + str(uuid.uuid4())[:8], 'description': 'Temporary AI smoke fixture'})
            active.append(system)
            created_systems.append(system['id'])
        integration = call('POST', '/integrations', {
            'name': 'AI smoke ' + str(uuid.uuid4())[:8], 'description': 'Temporary model/API verification',
            'sourceSystemId': active[0]['id'], 'destinationSystemId': active[1]['id'],
            'middlewareSystemId': None, 'hasMiddleware': False, 'isSync': False,
            'sourcePayloadFormat': source_format, 'destinationPayloadFormat': target_format})
        integration_id = integration['id']
        path = f'/integrations/{integration_id}/mappings'
        capability = call('GET', path + '/ai/capabilities')
        assert capability['available'], capability['code']
        def example(format, source):
            if format == 'JSON':
                return ({'customerCode': 'CUST-001', 'fullName': 'Deniz Örnek', 'email': 'demo@example.com'}
                        if source else {'clientId': 'CUST-001', 'displayName': 'Deniz Örnek', 'email': 'demo@example.com'}), '/email'
            if format == 'IDOC':
                return ('<DEBMAS07><IDOC BEGIN="1"><E1KNA1M SEGMENT="1"><KUNNR>CUST-001</KUNNR>'
                        '<NAME1>Deniz Örnek</NAME1><SMTP_ADDR>demo@example.com</SMTP_ADDR></E1KNA1M></IDOC></DEBMAS07>'), '/DEBMAS07/IDOC/E1KNA1M/SMTP_ADDR'
            if source:
                return '<Customer><customerCode>CUST-001</customerCode><fullName>Deniz Örnek</fullName><email>demo@example.com</email></Customer>', '/Customer/email'
            return '<Client><clientId>CUST-001</clientId><displayName>Deniz Örnek</displayName><email>demo@example.com</email></Client>', '/Client/email'

        source_sample, source_email = example(source_format, True)
        target_sample, target_email = example(target_format, False)
        schema_request = {'schemaType': 'SOURCE_TO_DEST_REQUEST', 'format': source_format, 'targetFormat': target_format,
                          'samplePayload' if source_format == 'JSON' else 'rawContent': source_sample,
                          'targetSamplePayload' if target_format == 'JSON' else 'targetRawContent': target_sample}
        schema = call('POST', path + '/schemas', schema_request)
        schema_id = schema['id']
        context = call('GET', path + '/context')
        manual = call('POST', path + '/fields/append', {
            'expectedContextRevision': context['contextRevision'],
            'mappings': [{'sourceSchemaId': schema_id, 'sourceFieldPath': source_email,
                          'targetSchemaId': schema_id, 'targetFieldPath': target_email}]}, key=str(uuid.uuid4()))
        manual_id = manual['addedMappings'][0]['id']
        revision = manual['contextRevision']
        request = {'sourceSchemaId': schema_id, 'targetSchemaId': schema_id,
                   'expectedContextRevision': revision, 'options': {'useSampleValues': True}}
        create_key = str(uuid.uuid4())
        job = call('POST', path + '/ai/jobs', request, key=create_key)
        assert call('POST', path + '/ai/jobs', request, key=create_key)['jobId'] == job['jobId']
        started = time.monotonic()
        previous = None

        request_timeout = int(values.get('AI_REQUEST_TIMEOUT_SECONDS', '300'))
        deadline = started + max(1, min(600, request_timeout)) + 60
        while time.monotonic() < deadline:
            job = call('GET', path + '/ai/jobs/' + job['jobId'])
            if job['status'] != previous:
                print(source_format + ' -> ' + target_format + ' AI job: ' + job['status'], flush=True)
                previous = job['status']
            if job['status'] == 'SUCCEEDED':
                break
            assert job['status'] in ('QUEUED', 'RUNNING'), (job['errorCode'], job['message'])
            time.sleep(2)
        else:
            raise RuntimeError('Timed out waiting for the AI smoke job')
        result = call('GET', path + '/ai/jobs/' + job['jobId'] + '/result')
        assert not result['stale']
        if not result['suggestions']:
            print(json.dumps({'sourceFormat': source_format, 'targetFormat': target_format,
                              'unmatchedSourceFields': result['unmatchedSourceFields'],
                              'unmatchedTargetFields': result['unmatchedTargetFields'],
                              'warnings': result['warnings']}, ensure_ascii=False), flush=True)
        assert result['suggestions'], 'Model returned no suggestions for the simple smoke fixture'
        assert all(item['targetFieldPath'] != target_email for item in result['suggestions'])
        def business_paths(format, source):
            if format == 'IDOC':
                return ('/DEBMAS07/IDOC/E1KNA1M/KUNNR', '/DEBMAS07/IDOC/E1KNA1M/NAME1')
            names = ('customerCode', 'fullName') if source else ('clientId', 'displayName')
            prefix = '' if format == 'JSON' else '/Customer' if source else '/Client'
            return tuple(prefix + '/' + name for name in names)

        expected_connections = set(zip(business_paths(source_format, True), business_paths(target_format, False)))
        actual_connections = {(item['sourceFieldPath'], item['targetFieldPath']) for item in result['suggestions']}
        assert actual_connections == expected_connections, {
            'expectedConnections': sorted(expected_connections), 'actualConnections': sorted(actual_connections)}
        append = {'expectedContextRevision': revision, 'aiJobId': job['jobId'], 'mappings': [
            {key: item[key] for key in ('suggestionId', 'sourceSchemaId', 'sourceFieldPath', 'targetSchemaId', 'targetFieldPath')}
            for item in result['suggestions']]}
        save_key = str(uuid.uuid4())
        saved = call('POST', path + '/fields/append', append, key=save_key)
        replay = call('POST', path + '/fields/append', append, key=save_key)
        assert [item['id'] for item in saved['addedMappings']] == [item['id'] for item in replay['addedMappings']]
        assert all(item['origin'] == 'AI_ACCEPTED' for item in saved['addedMappings'])
        links = call('GET', path + '/fields')
        assert manual_id in [item['id'] for item in links]
        assert len(links) == 1 + len(result['suggestions'])
        try:
            call('POST', path + '/fields/append', append, key=str(uuid.uuid4()))
        except RuntimeError as error:
            assert 'HTTP 409' in str(error), str(error)
        else:
            raise AssertionError('Stale context should have been rejected')
        print(json.dumps({'sourceFormat': source_format, 'targetFormat': target_format,
                          'model': result['model'], 'elapsedSeconds': round(time.monotonic() - started, 1),
                          'connections': [{'source': item['sourceFieldPath'], 'target': item['targetFieldPath']}
                                          for item in result['suggestions']],
                          'manualMappingPreserved': True, 'idempotencyVerified': True, 'staleWriteRejected': True},
                         ensure_ascii=False), flush=True)
    finally:
        if integration_id is not None:
            call('DELETE', f'/integrations/{integration_id}')
        for system_id in created_systems:
            call('DELETE', f'/systems/{system_id}')
        call('POST', '/auth/logout')

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-format', choices=['JSON', 'XML', 'IDOC'], default='JSON')
    parser.add_argument('--target-format', choices=['JSON', 'XML', 'IDOC'], default='JSON')
    parser.add_argument('--matrix', action='store_true', help='Run all nine format pairs sequentially')
    args = parser.parse_args()
    if args.matrix:
        for source in ('JSON', 'XML', 'IDOC'):
            for target in ('JSON', 'XML', 'IDOC'):
                run_smoke(source, target)
    else:
        run_smoke(args.source_format, args.target_format)

if __name__ == '__main__':
    main()
