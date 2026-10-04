// Finds a Java 21 JDK for the Maven gates (spec 2: Java 21). Uses JAVA_HOME when it is
// already a 21 JDK; otherwise looks in the usual install places.
import { spawnSync } from 'node:child_process';
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';

function isJdk21(home) {
  const release = path.join(home ?? '', 'release');
  return !!home && existsSync(release) && /JAVA_VERSION="21[."]/.test(readFileSync(release, 'utf8'));
}

function candidates() {
  const list = [process.env.JAVA_HOME];
  const mac = spawnSync('/usr/libexec/java_home', ['-v', '21'], { encoding: 'utf8' });
  if (mac.status === 0) list.push(mac.stdout.trim());
  for (const dir of [path.join(os.homedir(), '.sdkman/candidates/java'), '/usr/lib/jvm', '/opt/java']) {
    if (!existsSync(dir)) continue;
    for (const d of readdirSync(dir).filter((n) => n.includes('21')).sort().reverse()) list.push(path.join(dir, d));
  }
  return list;
}

export function javaEnv() {
  const home = candidates().find(isJdk21);
  if (!home) return {};
  return { JAVA_HOME: home, PATH: `${path.join(home, 'bin')}${path.delimiter}${process.env.PATH}` };
}

if (process.argv[1] && process.argv[1].endsWith('java-env.mjs')) {
  console.log(javaEnv().JAVA_HOME ?? '');
}
