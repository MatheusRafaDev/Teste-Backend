import http from 'k6/http';
import { check, sleep } from 'k6';
import { randomString } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

export const options = {
  stages: [
    { duration: '30s', target: 50 },  // rampa para 50 usuários virtuais em 30s
    { duration: '1m', target: 50 },   // mantém 50 usuários ativos por 1m
    { duration: '10s', target: 0 },   // desce para 0 usuários
  ],
};

const BASE_URL = 'http://localhost:8080';

export default function () {
  const payload = JSON.stringify({
    contaOrigem: 'CONTA-001',
    contaDestino: 'CONTA-002',
    valor: Math.random() * 100 + 10,
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': randomString(15),
    },
  };

  const res = http.post(`${BASE_URL}/transferencias`, payload, params);

  check(res, {
    'status é 201 ou 422': (r) => r.status === 201 || r.status === 422 || r.status === 409,
  });

  sleep(Math.random() * 0.5); // pequena pausa aleatória
}
