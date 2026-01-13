# 🛑 Como Parar o Loop de Reiniciar App

## ⚠️ Problema

Se o app está reiniciando continuamente, pode ser porque:
1. O comando no banco não foi marcado como executado
2. Há múltiplos comandos pendentes
3. O comando foi recriado após ser marcado como executado

## 🔧 Soluções

### Solução 1: Marcar Todos os Comandos como Executados (Recomendado)

No Supabase SQL Editor, execute:

```sql
-- Marca TODOS os comandos de restart_app como executados para um dispositivo
UPDATE device_commands 
SET executed = true, 
    executed_at = NOW()
WHERE device_id = 'SEU_DEVICE_ID_AQUI'
  AND command = 'restart_app'
  AND executed = false;
```

**Substitua `SEU_DEVICE_ID_AQUI`** pelo Device ID do dispositivo.

**Exemplo:**
```sql
UPDATE device_commands 
SET executed = true, 
    executed_at = NOW()
WHERE device_id = 'a2674df4a688c7d7'
  AND command = 'restart_app'
  AND executed = false;
```

### Solução 2: Deletar Comandos Pendentes

Se preferir deletar os comandos:

```sql
-- Deleta todos os comandos pendentes de restart_app
DELETE FROM device_commands
WHERE device_id = 'SEU_DEVICE_ID_AQUI'
  AND command = 'restart_app'
  AND executed = false;
```

### Solução 3: Verificar e Limpar Todos os Dispositivos

Para limpar comandos pendentes de TODOS os dispositivos:

```sql
-- Ver quantos comandos pendentes existem
SELECT device_id, COUNT(*) as pendentes
FROM device_commands
WHERE command = 'restart_app'
  AND executed = false
GROUP BY device_id;

-- Marcar todos como executados
UPDATE device_commands 
SET executed = true, 
    executed_at = NOW()
WHERE command = 'restart_app'
  AND executed = false;
```

### Solução 4: Verificar Status dos Comandos

Para ver o status atual:

```sql
-- Ver todos os comandos de um dispositivo
SELECT 
    id,
    device_id,
    command,
    executed,
    created_at,
    executed_at
FROM device_commands
WHERE device_id = 'SEU_DEVICE_ID_AQUI'
  AND command = 'restart_app'
ORDER BY created_at DESC;
```

## 🛡️ Proteções Implementadas

O app agora tem proteções automáticas:

1. **Cooldown de 5 minutos**: Após reiniciar, não reinicia novamente por 5 minutos
2. **Flag de reinício**: Evita múltiplos reinícios simultâneos
3. **Marca múltiplos comandos**: Se houver vários comandos pendentes, marca todos
4. **Verificação dupla**: Verifica novamente após marcar como executado

## 📋 Verificar se Funcionou

### No Supabase:

```sql
-- Verificar se ainda há comandos pendentes
SELECT COUNT(*) as pendentes
FROM device_commands
WHERE device_id = 'SEU_DEVICE_ID_AQUI'
  AND command = 'restart_app'
  AND executed = false;
```

**Deve retornar:** `0` (zero comandos pendentes)

### Via Logs (ADB):

```bash
adb logcat | grep -E "AppRestartMonitor|Comando marcado"
```

**Procure por:**
- `✅ Comando marcado como executado`
- `ℹ️ Nenhum comando de reiniciar app pendente`
- `⏳ Cooldown ativo` (se ainda estiver no cooldown)

## ⚡ Solução Rápida (Uma Linha)

Execute no Supabase SQL Editor:

```sql
UPDATE device_commands SET executed = true, executed_at = NOW() WHERE command = 'restart_app' AND executed = false;
```

Isso marca TODOS os comandos pendentes de TODOS os dispositivos como executados.

## 💡 Prevenção

Para evitar loops no futuro:

1. **Sempre verifique** se o comando foi marcado como executado antes de criar um novo
2. **Use o cooldown**: O app tem cooldown de 5 minutos, não crie comandos muito frequentes
3. **Verifique logs**: Se o app reiniciar múltiplas vezes, verifique os logs

## 🔍 Debug

Se o problema persistir:

1. **Verifique os logs do app:**
   ```bash
   adb logcat | grep AppRestartMonitor
   ```

2. **Verifique o banco:**
   ```sql
   SELECT * FROM device_commands 
   WHERE device_id = 'SEU_DEVICE_ID' 
   ORDER BY created_at DESC 
   LIMIT 10;
   ```

3. **Verifique se há múltiplos comandos sendo criados:**
   ```sql
   SELECT device_id, COUNT(*) 
   FROM device_commands 
   WHERE command = 'restart_app' 
     AND executed = false 
   GROUP BY device_id;
   ```

---

**Dica:** Se o problema persistir mesmo após marcar como executado, pode haver um problema na criação automática de comandos. Verifique se há algum script ou processo criando comandos automaticamente.
