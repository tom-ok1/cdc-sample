# Local CDC Data Platform on k8s – Planning Memo

## 0. ゴール

- ローカル k8s クラスタ上に **「データ基盤クラスタ」っぽい最小構成** を立てる。
- **ホスト上の Postgres（local）** に Debezium（Kafka Connect）から接続し、CDC イベントを Kafka に流す。
- Kafka から **シンプルなインデクサー（Consumer）** を動かして、

  - 受け取ったイベントを **オンメモリの HashMap に積むだけ** のアプリを動かす。

- 本番構成の「ミニ版」をローカルで動かし、アーキテクチャの感覚を掴むことが目的。

---

## 1. 全体アーキテクチャ

### 1.1 コンポーネント

- **外部（k8s の外）**

  - `local-postgres`：ローカルホスト上の PostgreSQL（Docker / brew / 手動など）

- **k8s クラスタ（local-k8s / kind / minikube など）**

  - Namespace: `data-platform`
  - Kafka broker（シングルブローカーで OK）(TODO)
  - Kafka Connect（Debezium Postgres Connector プラグイン入り）
  - CDC Consumer アプリ（インメモリ HashMap インデクサー）
  - （オプション）Schema Registry は今回無しでよい（JSON 垂れ流しで OK） (TODO)

### 1.2 データフロー

```text
local Postgres (host)
  ↓ (logical replication / WAL)
Debezium (Kafka Connect on k8s)
  ↓
Kafka topic (cdc.inventory.customers など)
  ↓
CDC Consumer app (on k8s)
  ↓
in-memory HashMap (プロセス内)
```

---

## 2. ローカル k8s クラスタ構成案

### 2.1 クラスタ前提

- ツール候補

  - Docker Desktop の内蔵 k8s

- シンプルさ優先で、まずは：

  - ノード 1 台
  - Storage はとりあえず HostPath or emptyDir（Kafka の永続性は今回はあまり気にしない） (TODO)

### 2.2 Namespace

- `data-platform` namespace を作成し、すべてのリソースをここに集約。

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: data-platform
```

---

## 3. 外部 Postgres との接続戦略

### 3.1 ネットワーク

- k8s からホスト上の Postgres に接続する必要がある。
- 例：

  - Docker Desktop: `host.docker.internal:5432`
  - kind / minikube: ホスト IP または `Node IP:5432` を使う

- Debezium コネクタ設定の `database.hostname` にこれを指定。

### 3.2 Postgres 側設定（外部）

※これは k8s 外だが、やることメモとして：

- `postgresql.conf`

  - `wal_level = logical`
  - `max_wal_senders >= 4`
  - `max_replication_slots >= 4`

- `pg_hba.conf`

  - k8s ノードからの接続許可（ローカルなのでざっくりでも可）

---

## 4. Kubernetes リソース設計

### 4.1 Kafka Broker

最低限のシングルブローカー構成。

#### リソース概要

- `Deployment` or `StatefulSet`：`kafka-broker`
- `Service`：`kafka`（ClusterIP）
- ポート：`9092`
- 外部から接続する予定は今のところなし（k8s 内の Connect / Consumer のみ利用）

---

### 4.2 Kafka Connect + Debezium

#### 概要

- `Deployment`: `kafka-connect`
- `Service`: `kafka-connect`（REST API: 8083）
- Debezium Postgres Connector が入ったイメージを使う or initContainer で JAR をコピー

---

### 4.3 CDC Consumer（インメモリ HashMap）

#### 役割

- Kafka の CDC トピックを購読。
- key/value を読み出し、プロセス内の `HashMap<PrimaryKey, LatestState>` に保存。
- 動作確認用として：

  - 受け取ったイベントをログ出力
  - 定期的に HashMap のサイズや一部キーの値をログに出す

---

## 5. ローカル開発フロー（ざっくり）

1. **ローカル Postgres 準備**

   - Postgres を起動
   - logical replication 設定
   - `sample_table` 作成 & 適当なテストデータ投入

2. **local k8s クラスタ作成**

   - `kind create cluster --name data-platform` など
   - `kubectl apply -f namespace.yaml`

3. **Kafka / Zookeeper デプロイ**

   - `kubectl apply -f zookeeper.yaml`
   - `kubectl apply -f kafka-broker.yaml`

4. **Kafka Connect デプロイ**

   - `kubectl apply -f kafka-connect.yaml`
   - `kubectl port-forward svc/kafka-connect 8083:8083 -n data-platform`
   - コネクタ JSON を `curl` で POST

5. **CDC Consumer デプロイ**

   - Consumer アプリをローカルで実装
   - Docker イメージビルドして kind にロード
   - `kubectl apply -f cdc-consumer.yaml`
   - `kubectl logs -f deploy/cdc-consumer -n data-platform` で HashMap の様子を確認

6. **Postgres で INSERT/UPDATE/DELETE**

   - CDC イベントが Kafka → Consumer に流れ、HashMap が更新されることを確認。

---
