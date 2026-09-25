# Documentação da API - Core Bancário

Esta documentação detalha as rotas, regras de negócio e contratos de interface da API do sistema bancário, construída sobre **Java 21** e **Spring Boot 3**.

---

## OpenAPI / Swagger

A especificação completa, gerada dinamicamente, está disponível nos seguintes endpoints locais:

- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON**: `http://localhost:8080/v3/api-docs`

---

## 🧠 Padrões Globais

### 1. Representação de Dinheiro
Todo valor de moeda interna é calculado e salvo usando `Long` (inteiro) em **centavos** no banco de dados para evitar problemas matemáticos de precisão com pontos flutuantes (`float`/`double`).
Na API JSON, no entanto, os valores são serializados e desserializados utilizando tipo decimal padrão (ex: `150.50`), o backend converte automaticamente para `15050` centavos.

### 2. Idempotência (`Idempotency-Key`)
Para garantir que o usuário não seja cobrado duas vezes em caso de *retry* na rede, endpoints de alteração de estado (`POST`) exigem um header obrigatório `Idempotency-Key`.
O sistema armazena o hash atrelado a essa chave.
- **Requisição Repetida**: O sistema identifica que a chave e corpo são idênticos e retorna a resposta de cache (`200 OK` / `201 Created` original), sem reprocessar.
- **Conflito**: O sistema identifica a chave sendo usada com um payload JSON diferente e rejeita com `409 Conflict`.

### 3. Tratamento de Erro Padrão
Todas as falhas de regra de negócio devolvem o seguinte padrão:
```json
{
  "timestamp": "2026-09-10T22:05:00.000-03:00",
  "status": 422,
  "error": "Unprocessable Entity",
  "codigo": "SALDO_INSUFICIENTE",
  "message": "Saldo insuficiente para transferir R$ 100,00 mais taxa de R$ 1,00",
  "path": "/transferencias"
}
```
*O campo `codigo` é fixo e deve ser usado pelas aplicações cliente.*

---

## 📡 Endpoints

### 1. Extrato de Conta
Consulta os movimentos da conta em ordem cronológica inversa (mais novos primeiro).

**`GET /contas/{numero}/extrato`**

- **Parâmetros**:
  - `numero` (Path): Número da conta (Ex: `CONTA-001`).
  - `de` (Query, Opcional): Timestamp ISO a partir do qual buscar.
  - `ate` (Query, Opcional): Timestamp ISO até o qual buscar.
  - `page` (Query, Default `0`): Paginação.
  - `size` (Query, Default `20`): Quantidade de itens por página.
- **Respostas**:
  - `200 OK`: Retorna os movimentos.
    ```json
    {
      "saldoAtual": 1250.00,
      "movimentos": [
        {
          "movimentoId": "uuid-aqui",
          "tipo": "ENTRADA",
          "valor": 150.00,
          "saldoApos": 1250.00,
          "hash": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
          "data": "2026-09-10T15:30:00.000Z"
        }
      ]
    }
    ```
  - `404 Not Found`: Caso a conta não exista.

### 2. Transferência Bancária
Transfere dinheiro da `contaOrigem` para a `contaDestino`. A operação é atômica, validando saldo e limites simultaneamente sob locks pessimistas do PostgreSQL.

**`POST /transferencias`**

- **Headers Obligatórios**: `Idempotency-Key`
- **Regra de Taxa**:
  - Até R$ 100,00: **Isento**
  - Acima de R$ 100,00: **1%**, limitado a **R$ 20,00** de teto e **R$ 1,00** de piso.
- **Payload**:
  ```json
  {
    "contaOrigem": "CONTA-001",
    "contaDestino": "CONTA-002",
    "valor": 150.00
  }
  ```
- **Respostas**:
  - `201 Created`:
    ```json
    {
      "transferenciaId": "uuid",
      "contaOrigem": "CONTA-001",
      "contaDestino": "CONTA-002",
      "valor": 150.00,
      "taxa": 1.50,
      "totalDebitado": 151.50,
      "saldoOrigemApos": 848.50,
      "concluidaEm": "2026-09-10T15:30:00.000Z"
    }
    ```
  - `400 Bad Request`: Payload inválido ou header faltando.
  - `403 Forbidden`: Tentativa não autorizada de movimentar contas reservadas do sistema (ex: `SISTEMA-ENTRADA` ou `SISTEMA-TAXAS`).
  - `404 Not Found`: Conta não encontrada.
  - `409 Conflict`: Contas bloqueadas/encerradas, ou payload conflitante com idempotência.
  - `422 Unprocessable Entity`: Código `SALDO_INSUFICIENTE` ou `LIMITE_DIARIO_EXCEDIDO` (Limite padrão: R$ 2.000,00/dia).

### 3. Agendamento de Transferência
Programa uma transferência para execução futura. Nenhuma validação de saldo é feita neste instante, apenas no momento em que o job de background processar.

**`POST /transferencias-agendadas`**

- **Headers Obligatórios**: `Idempotency-Key`
- **Payload**:
  ```json
  {
    "contaOrigem": "CONTA-001",
    "contaDestino": "CONTA-002",
    "valor": 150.00,
    "executarEm": "2026-09-15T14:00:00-03:00"
  }
  ```
- **Respostas**:
  - `201 Created`:
    ```json
    {
      "agendamentoId": "uuid",
      "estado": "AGENDADO"
    }
    ```
  - `403 Forbidden`: Tentativa não autorizada de movimentar contas reservadas do sistema.
  - `404 Not Found`: Conta de origem ou destino inexistente.
  - `422 Unprocessable Entity`: Data de execução `executarEm` está no passado.

### 3.1. Cancelamento de Agendamento
Cancela um agendamento futuro caso ele ainda não tenha sido processado pelo sistema.

**`POST /transferencias-agendadas/{id}/cancelamento`**

- **Parâmetros**:
  - `id` (Path): UUID do agendamento a ser cancelado.
- **Respostas**:
  - `200 OK`: O agendamento foi cancelado com sucesso (passou a constar como FALHADO pelo usuário).
  - `404 Not Found`: Agendamento inexistente.
  - `422 Unprocessable Entity`: Agendamento não está no estado `AGENDADO` (já concluído ou em andamento).

### 4. Estorno
Reverte integralmente o valor e a taxa de uma transferência já concluída. Só pode ocorrer uma vez por transferência. O estorno NÃO devolve o limite diário consumido originalmente.

**`POST /transferencias/{id}/estorno`**

- **Headers Obligatórios**: `Idempotency-Key`
- **Payload**:
  ```json
  {
    "motivo": "Pedido de reversão."
  }
  ```
- **Respostas**:
  - `201 Created`: O estorno foi realizado, gerando o movimento oposto.
  - `404 Not Found`: A transferência alvo não existe.
  - `409 Conflict`: Transferência com estado diferente de `CONFIRMADA` ou já possui um estorno.
  - `422 Unprocessable Entity`: A conta do destinatário **não possui saldo** para devolver o valor (regra de proteção ao sistema para não negativar contas).

### 5. Depósito (Carga Manual)
Credita valor diretamente a uma conta a partir da conta do sistema `SISTEMA-ENTRADA`. Utilizado em ambientes internos de liquidação.

**`POST /contas/{numero}/depositos`**

- **Headers Obligatórios**: `Idempotency-Key`
- **Payload**:
  ```json
  {
    "valor": 500.00
  }
  ```
- **Respostas**:
  - `201 Created`: Retorna o id da transferência interna e a data de conclusão.
  - `404 Not Found`: A conta de usuário solicitada não existe.
  - `409 Conflict`: A conta de usuário informada está ENCERRADA. (Conta BLOQUEADA pode receber depósito).
  - `422 Unprocessable Entity`: Valores negativos ou absurdos.

### 6. Resumo e Indicadores (Dashboard)
Retorna as estatísticas gerenciais agregadas dinamicamente via JPQL, filtrando ocorrências criadas dentro da "janelaMinutos". As validações incluem verificar se a soma integral de todos os movimentos do banco é Zero.

**`GET /resumo`**

- **Parâmetros**: `janelaMinutos` (Opcional, Default 60)
- **Respostas**:
  - `200 OK`:
    ```json
    {
      "contasAtivasAgora": 5,
      "saldoTotalUsuariosAgora": 4850.00,
      "agendamentosPendentesAgora": 3,
      "transferenciasNaJanela": 12,
      "valorTransferidoNaJanela": 3420.00,
      "taxasNaJanela": 12.99,
      "ticketMedioNaJanela": 285.00,
      "estornosNaJanela": 1,
      "somaDosMovimentosEhZero": true
    }
    ```

### 7. Administração (Wipe-out)
Ação de limpeza exclusiva de testes e avaliação do desafio técnico. Executa um drop/migrate no Flyway, limpando todas as informações, movimentos e saldos, voltando o sistema ao seu estado de migração `V2`.
*(Nota: Este endpoint está propositalmente ativado em todos os profiles para facilitar o teste do avaliador no ambiente padrão Docker).*

**`DELETE /dados`**
- **Respostas**: `200 OK`
