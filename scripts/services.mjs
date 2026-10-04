import { spawn } from 'node:child_process';

// Each service owns a POSIX process group, including Maven's Java subprocess.
// Stop only groups created here; never stop an existing Ollama or Docker service.
export async function runServices(services, { shutdownMs = 3000 } = {}) {
  if (process.platform === 'win32') {
    throw new Error('Use WSL for native development, or run the Docker commands on Windows.');
  }
  return new Promise((resolve) => {
    const children = [];
    let stopping = false;
    const signalGroup = (child, signal) => {
      if (!child.pid) return;
      try {
        process.kill(-child.pid, signal);
      } catch (error) {
        if (error.code !== 'ESRCH') console.error(error.message);
      }
    };
    const stop = (code) => {
      if (stopping) return;
      stopping = true;
      for (const child of children) signalGroup(child, 'SIGTERM');
      setTimeout(() => {
        // A wrapper can exit before its subprocess, so address the whole group.
        for (const child of children) signalGroup(child, 'SIGKILL');
        process.off('SIGINT', interrupt);
        process.off('SIGTERM', terminate);
        resolve(code);
      }, shutdownMs);
    };
    const interrupt = () => stop(130);
    const terminate = () => stop(143);
    process.on('SIGINT', interrupt);
    process.on('SIGTERM', terminate);
    for (const service of services) {
      console.log(`Starting ${service.name}…`);
      const child = spawn(service.command, service.args ?? [], {
        cwd: service.cwd,
        env: service.env ?? process.env,
        stdio: 'inherit',
        detached: true,
      });
      children.push(child);
      child.on('error', (error) => {
        console.error(`${service.name}: ${error.message}`);
        stop(1);
      });
      child.on('exit', (code, signal) => {
        if (!stopping)
          console.error(`${service.name} exited (${signal ?? code}); stopping the other service.`);
        stop(code || 1);
      });
    }
  });
}
