# RUNBOOK — rodar o parking-lot-service localmente

Guia operacional prático. Comandos testados no Windows (Docker Desktop) e válidos em
Linux/macOS com a ressalva de rede indicada. Para o "porquê" das decisões, veja o
[`README.md`](README.md) e os [`docs/adr/`](docs/adr).

**Portas usadas:** app `3003`, MySQL `3306`, simulador `3000`.

---

## 0. Pré-requisitos

- **Docker** (Docker Desktop no Windows/macOS). É o único requisito para rodar a aplicação.
- **JDK 21** só é necessário para rodar os testes fora do container (`./mvnw`). A imagem
  Docker traz o próprio JDK/JRE.
- Nenhum outro serviço ocupando 3003 / 3306 / 3000.

---

## 1. Subir tudo (caminho feliz)

> **A ordem importa.** O app sincroniza a configuração chamando `GET /garage` no simulador
> **no startup**, com tentativas limitadas (sem crash-loop). Se o app subir **antes** do
> simulador existir, ele esgota as tentativas e fica `NOT READY` sem se recuperar sozinho.
> Por isso: **simulador primeiro, app depois.**

```bash
# 1) Simulador (Windows/macOS — Docker Desktop):
docker run -d --name estapar-sim -p 3000:3000 \
  -e CLIENT_WEBHOOK_URL=http://host.docker.internal:3003/webhook \
  cfontes0estapar/garage-sim:1.0.0

#    No Linux, use host networking (localhost do container == host):
#    docker run -d --name estapar-sim --network=host cfontes0estapar/garage-sim:1.0.0

# 2) App + MySQL (a partir da raiz do projeto: E:\Estapar\parking-lot-service)
docker compose up -d --build
```

Alternativa sem `docker run` separado — tudo na mesma rede do compose (qualquer SO):

```bash
docker compose --profile simulator up -d --build
```

Verifique que ficou pronto (esperar `garageConfiguration` = `UP`):

```bash
curl http://localhost:3003/actuator/health
# → {"status":"UP", ... "garageConfiguration":{"status":"UP"} ...}
```

Quando estiver `UP`, abra o **Swagger UI**: <http://localhost:3003/swagger-ui.html>

---

## 2. Saúde e prontidão (o que cada probe significa)

```bash
curl http://localhost:3003/actuator/health/liveness    # UP assim que o processo sobe
curl http://localhost:3003/actuator/health/readiness   # UP só após sincronizar a garagem
```

- **Liveness UP, Readiness DOWN** → o app está vivo mas ainda não sincronizou com o simulador.
  Enquanto isso, `POST /webhook` responde **503** (por design). Veja o Troubleshooting §6.
- **Readiness UP** → setores/vagas carregados; pronto para receber eventos e consultas.

---

## 3. Exercitar a API

> **Aspas na URL do `/revenue`!** O `&` separando `date` e `sector` é interpretado pelo shell
> se a URL não estiver entre aspas — no terminal isso aparece como um `curl` falhando com
> `HTTP 000`. Sempre use aspas simples.

### Webhook — payloads **válidos** (cada evento carrega só os seus campos)

O evento é validado condicionalmente: um `ENTRY` que também traga `exit_time`/`lat`/`lng` é
rejeitado com **400** (campos ambíguos). Use um payload por tipo:

```bash
H='-H Content-Type:application/json'; U='http://localhost:3003/webhook'

# ENTRY
curl -s $H -d '{"license_plate":"ABC1234","entry_time":"2025-01-01T12:00:00.000Z","event_type":"ENTRY"}' $U
# PARKED (use coordenadas de uma vaga existente — ver §4)
curl -s $H -d '{"license_plate":"ABC1234","lat":-23.561684,"lng":-46.655981,"event_type":"PARKED"}' $U
# EXIT (2h depois → cobrança)
curl -s $H -d '{"license_plate":"ABC1234","exit_time":"2025-01-01T14:00:00.000Z","event_type":"EXIT"}' $U
```

> No **Swagger "Try it out"**, o exemplo pré-preenchido traz **todos** os campos e por isso
> dá 400. Apague os campos que não pertencem ao evento (ex.: para ENTRY, deixe só
> `license_plate`, `entry_time`, `event_type`).

### Revenue

```bash
curl -s 'http://localhost:3003/revenue?date=2025-01-01&sector=A'
# → {"amount":0.00,"currency":"BRL","timestamp":"..."}   (0.00 se não houver cobrança na data)
```

- A **data é a data de negócio** (fuso `America/Sao_Paulo`), não UTC. Uma saída às `00:30 UTC`
  conta para o **dia anterior** no horário de São Paulo.
- Setor inexistente → **404** `ProblemDetail`; data malformada → **400**.

---

## 4. Ver receita **diferente de zero**

O simulador dá saídas em segundos (≤ 30 min = grátis → cobrança `0.00`). Para ver dinheiro,
provoque uma estadia longa você mesmo:

```bash
# 4.1) achar uma vaga AVAILABLE no setor A
docker exec parking-lot-service-mysql-1 mysql -ugarage -pgarage garage -N -e \
  "SELECT external_id, latitude, longitude FROM parking_spot WHERE sector_code='A' AND status='AVAILABLE' LIMIT 1;"

# 4.2) ENTRY → PARKED (nessa vaga) → EXIT 2h depois → consultar receita
H='-H Content-Type:application/json'; U='http://localhost:3003/webhook'
curl -s $H -d '{"license_plate":"DEMO0001","entry_time":"2025-01-01T12:00:00.000Z","event_type":"ENTRY"}' $U
curl -s $H -d '{"license_plate":"DEMO0001","lat":<LAT>,"lng":<LNG>,"event_type":"PARKED"}' $U
curl -s $H -d '{"license_plate":"DEMO0001","exit_time":"2025-01-01T14:00:00.000Z","event_type":"EXIT"}' $U
curl -s 'http://localhost:3003/revenue?date=2025-01-01&sector=A'
```

O valor depende da ocupação do setor no momento do PARKED (faixa de multiplicador) × horas.

---

## 5. Rodar os testes

```bash
./mvnw clean verify   # formatter + unit + integração (Testcontainers MySQL) + ArchUnit + package
./mvnw test           # só unitários (rápido, sem Docker)
```

`verify` sobe um MySQL efêmero via Testcontainers (precisa do Docker rodando). Não usa o
simulador real. Relatório de cobertura em `target/site/jacoco/index.html`.

Smoke test end-to-end contra o simulador **real** (app já no ar):

```bash
bash scripts/smoke-simulator.sh          # Linux/macOS
pwsh scripts/smoke-simulator.ps1         # Windows (PowerShell)
```

---

## 6. Troubleshooting (problemas reais e o fix)

| Sintoma | Causa | Fix |
|---|---|---|
| `readiness` fica **DOWN**, `/webhook` dá **503**, `/revenue` dá **404 "Sector does not exist"** | App subiu **antes** do simulador e esgotou as tentativas de sync | Reinicie o app para re-sincronizar: `docker compose restart garage-api`. Ou suba o simulador **primeiro** (§1). |
| `curl` para `/revenue` retorna **HTTP 000** / vazio | `&` na URL sem aspas — o shell corta o comando | Ponha a URL entre **aspas simples**: `'http://localhost:3003/revenue?date=...&sector=A'` |
| Swagger UI não abre | Stack não está no ar, ou app ainda não pronto | Confira `docker compose ps` e `curl .../actuator/health`; suba conforme §1 |
| `/webhook` no Swagger dá **400 "must not carry..."** | O exemplo pré-preenchido traz todos os campos | É a validação funcionando; envie só os campos do tipo do evento (§3) |
| `docker compose up` falha na porta **3306** | Já existe um MySQL local ocupando 3306 | Pare o MySQL local, ou ajuste o mapeamento de porta no `compose.yml` |
| App não alcança o simulador (`sync_failed`) | `host.docker.internal` não resolve (Linux sem Docker Desktop) | Use `--network=host` no simulador (§1) ou o profile `simulator` do compose |
| PARKED dá **404** "spot not found" | Coordenadas não batem com nenhuma vaga | Pegue lat/lng reais do banco (§4.1) ou do `GET /garage` do simulador |

Logs úteis:

```bash
docker compose logs -f garage-api          # logs do app (procure garage_configuration_synchronized)
docker logs estapar-sim                     # logs do simulador
docker compose ps                           # estado dos containers
```

---

## 7. Encerrar

```bash
docker compose down -v        # para app + MySQL e remove o volume (zera os dados)
docker rm -f estapar-sim      # para o simulador
```

`down` sem `-v` preserva os dados do MySQL entre execuções; com `-v` começa do zero.

---

## 8. Rodar sem Docker (opcional)

Precisa de um MySQL acessível e das variáveis de `.env.example` exportadas:

```bash
export MYSQL_HOST=localhost MYSQL_USER=garage MYSQL_PASSWORD=garage MYSQL_DATABASE=garage
export GARAGE_SIMULATOR_BASE_URL=http://localhost:3000
./mvnw spring-boot:run
```

Flyway cria o schema no primeiro start (`ddl-auto=validate`). O caminho por Docker (§1) é o
recomendado por já trazer o MySQL configurado.
