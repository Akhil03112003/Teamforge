import {test} from 'node:test';
import assert from 'node:assert/strict';
import {summarize} from './result-summary.mjs';

test('absent metrics never report a threshold pass', () => {
  const result = summarize({}, 30, 180);
  assert.equal(result.status, 'no-measured-requests');
  assert.equal(result.thresholdsPassed, null);
  assert.equal(result.p95Below200ms, null);
});
test('summary formats preserve strict boundary and request/error gates', () => {
  const metrics = {board_duration:{values:{'p(50)':50,'p(95)':200,'p(99)':250}},board_failures:{values:{rate:0}},board_requests:{values:{count:18000}}};
  assert.equal(summarize({metrics},30,180).thresholdsPassed,false);
  const converted = {results:{metrics:Object.entries(metrics).map(([name,metric])=>({name,...metric}))}};
  assert.deepEqual(summarize(converted,30,180),summarize({metrics},30,180));
  metrics.board_duration.values['p(95)']=199;
  metrics.board_requests.values.count=17999;
  assert.equal(summarize({metrics},30,180).thresholdsPassed,false);
  metrics.board_requests.values.count=18000;
  metrics.board_failures.values.rate=.01;
  assert.equal(summarize({metrics},30,180).thresholdsPassed,false);
});
