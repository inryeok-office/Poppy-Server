import { planMotion, countProgramBlocks, describeHeading } from './motionPlan.ts';
import { validateAiSteps } from './validate.ts';
import { sanitizeAiInput } from './sanitizeInput.ts';
import { planFromUtterance } from './planner.ts';

let seed = 20261110;
const rand = () => {
  seed = (seed * 1103515245 + 12345) % 2147483648;
  return seed / 2147483648;
};
const pick = (items) => items[Math.floor(rand() * items.length)];
const round1 = (v) => Math.round(v * 10) / 10;

const S = { kind: 'start' };
const E = { kind: 'end' };
const fwd = (distanceM) => ({ kind: 'moveForward', distanceM });
const back = (distanceM) => ({ kind: 'move', distanceM });
const right = (degrees) => ({ kind: 'rotateRight', degrees });
const left = (degrees) => ({ kind: 'rotateLeft', degrees });
const wait = (seconds) => ({ kind: 'wait', seconds });
const repeat = (count, body) => ({ kind: 'repeat', count, body });
const sit = { kind: 'sit' };
const stand = { kind: 'standUp' };
const greet = { kind: 'greet' };
const stop = { kind: 'stop' };

const handPrograms = [
  [fwd(1), right(90), sit, stand, greet, wait(2)],
  [repeat(4, [fwd(0.5), right(90)])],
  [right(90)],
  [left(90)],
  [fwd(1), stop, fwd(3)],
  [sit, fwd(0.5)],
  [sit, right(90)],
  [sit, greet],
  [sit, wait(1), stand, fwd(0.5)],
  [sit],
  [repeat(10, [wait(10)]), repeat(2, [wait(10)])],
  [repeat(10, [wait(10)]), repeat(3, [wait(10)])],
  [],
  [repeat(4, [fwd(0.6)]), left(90), repeat(5, [fwd(0.6)])],
  [right(90), fwd(0.5)],
  [back(0.5)],
  [fwd(3), fwd(2.2)],
  [fwd(3), fwd(2.3)],
  [left(90), fwd(3), fwd(2.2)],
  [left(90), fwd(3), fwd(2.3)],
  [left(90), fwd(1), back(1)],
  [left(90), fwd(1), back(1.1)],
  [fwd(1), back(1)],
  [fwd(1), back(1.1)],
  [left(90), fwd(1)],
  [left(180), right(90), fwd(1)],
  [left(90), fwd(2), right(90), fwd(1)],
  [left(30), fwd(1)],
  [right(90), left(45), sit, wait(1), stand, greet],
  [repeat(3, [fwd(1.5), back(1.5)])],
  [fwd(3), fwd(3), back(3)],
];

const leafKinds = ['moveForward', 'move', 'rotateLeft', 'rotateRight', 'wait', 'stop', 'standUp', 'greet', 'heart', 'dance', 'sit'];
const randomLeaf = () => {
  const kind = pick(leafKinds);
  if (kind === 'moveForward' || kind === 'move') return { kind, distanceM: round1(0.1 + rand() * 2.9) };
  if (kind === 'rotateLeft' || kind === 'rotateRight') return { kind, degrees: pick([30, 45, 60, 90, 120, 135, 180, 270, 360, 1 + Math.floor(rand() * 360)]) };
  if (kind === 'wait') return { kind, seconds: 1 + Math.floor(rand() * 10) };
  return { kind };
};
const randomProgram = () => {
  const n = 1 + Math.floor(rand() * 6);
  const out = [];
  for (let i = 0; i < n; i += 1) {
    if (rand() < 0.25) {
      const body = Array.from({ length: 1 + Math.floor(rand() * 3) }, randomLeaf);
      out.push(repeat(1 + Math.floor(rand() * 6), body));
    } else {
      out.push(randomLeaf());
    }
  }
  return out;
};

const programs = [...handPrograms, ...Array.from({ length: 400 }, randomProgram)];
const motion = programs.map((body) => {
  const chain = [S, ...body, E];
  const plan = planMotion(chain);
  return {
    program: body,
    expected: {
      totalMeters: plan.totalMeters,
      totalSeconds: plan.totalSeconds,
      finalPosture: plan.finalPosture,
      headingDegrees: plan.headingDegrees,
      path: plan.path.map((p) => [p.xM, p.yM]),
      failurePoint: plan.failurePoint ? [plan.failurePoint.xM, plan.failurePoint.yM] : null,
      violation: plan.violation,
      executedKinds: plan.steps.map((s) => s.node.kind),
      structuralBlocks: countProgramBlocks(chain),
    },
  };
});

const validateInputs = [
  [fwd(1), left(90), repeat(3, [{ kind: 'heart' }])],
  [{ kind: 'moveForward', distanceM: 1, id: 'x', extra: true }],
  [{ kind: 'sit' }],
  [{ kind: 'flyAway' }],
  [fwd(10)],
  [left(400)],
  [repeat(2, [repeat(2, [{ kind: 'heart' }])])],
  [repeat(3, [])],
  Array.from({ length: 13 }, () => greet),
  Array.from({ length: 12 }, () => greet),
  [repeat(10, [fwd(1), { kind: 'heart' }])],
  [],
  'not-an-array',
  null,
  [fwd(0.15)],
  [fwd(0.3)],
  [fwd('1.5')],
  [wait(2.5)],
  [right(90.0)],
  [repeat(0, [greet])],
  [repeat(11, [greet])],
  [{ kind: 'move' }],
  [repeat(2, [greet, greet, greet, greet, greet, greet, greet, greet, greet, greet, greet])],
  [repeat(2, [greet, greet, greet, greet, greet, greet, greet, greet, greet, greet])],
  ...Array.from({ length: 200 }, () => randomProgram().map((n) => (rand() < 0.1 && 'distanceM' in n ? { ...n, distanceM: round1(n.distanceM * 3) } : n))),
];
const validate = validateInputs.map((input) => ({ input, expected: validateAiSteps(input) }));

const sanitizeInputs = ['  앞으로   1m\t가 줘\n', '\u0007하트\u001f해', 'a'.repeat(150), '', '   ', '네모 그려 줘'];
const sanitize = sanitizeInputs.map((input) => ({ input, expected: sanitizeAiInput(input) }));

const headings = [0, 90, 180, 270, -30, 359.5, 720, -450];
const describe = headings.map((input) => ({ input, expected: describeHeading(input) }));

const plannerInputs = [
  '네모를 그리면서 걸어 줘', '세모 그려', '앞으로 1m 가고 왼쪽으로 돌아', '뒤로 50cm 가', '앞으로 3칸 가고 하트 세 번',
  '오른쪽으로 45도 돌고 인사해', '3초 기다리고 춤춰', '반 바퀴 돌아', '한 바퀴 돌아', '앉아', '앉았다가 일어서',
  '0.5m 가고 하트 세 번', '앞으로 10m 가', '왼쪽으로 400도 돌아', '15초 기다려', '하트 12번', '멈춰', '정지',
  '그리고', '', '안녕', '왼쪽으로 90도 돌고, 앞으로 2m 가고 그리고 인사해', '2번 춤춰 다음에 오른쪽으로 30도',
  '앞으로 1.25m 가', '왼쪽으로 22.5도 돌아', '네모 3번', '일어서', '오른쪽 120° 돌아', '앞으로 0.05m', '7번 인사',
];
const planner = plannerInputs.map((input) => ({ input, expected: planFromUtterance(input) }));

process.stdout.write(
  JSON.stringify({ clientCommit: process.argv[2] ?? 'unknown', motion, validate, sanitize, describe, planner }, null, 0) + '\n',
);
