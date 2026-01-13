# 🔄 Como Reiniciar um Dispositivo Remotamente

Este guia mostra como reiniciar um dispositivo usando o Device ID via Supabase.

## 📋 Pré-requisitos

1. ✅ Tabela `reboot_commands` criada no Supabase
2. ✅ Device ID do dispositivo
3. ✅ App instalado e Device Admin ativado no dispositivo

## 🎯 Device ID do Dispositivo

**Device ID:** `330f8cacd4ec197c`

## 📝 Como Criar Comando de Reiniciar

### Opção 1: Via SQL Editor do Supabase (Recomendado)

1. Acesse o **Supabase Dashboard**: https://supabase.com/dashboard
2. Selecione seu projeto
3. Vá em **SQL Editor**
4. Execute o seguinte comando:

```sql
INSERT INTO reboot_commands (device_id, should_reboot, executed)
VALUES ('330f8cacd4ec197c', true, false);
```

5. Clique em **Run** para executar

### Opção 2: Via Table Editor do Supabase

1. Acesse o **Supabase Dashboard**
2. Selecione seu projeto
3. Vá em **Table Editor**
4. Selecione a tabela `reboot_commands`
5. Clique em **Insert row**
6. Preencha os campos:
   - `device_id`: `330f8cacd4ec197c`
   - `should_reboot`: `true`
   - `executed`: `false`
7. Clique em **Save**

## ⚡ O que Acontece Depois

1. O app verifica o Supabase a cada **30 segundos**
2. Quando encontrar o comando, ele:
   - Marca o comando como executado (`executed = true`)
   - Reinicia o dispositivo
3. Após reiniciar, o dispositivo:
   - Liga normalmente
   - O `BootReceiver` detecta o boot
   - Abre automaticamente o app configurado

## ✅ Verificar Status do Comando

Para verificar se o comando foi executado:

```sql
SELECT * FROM reboot_commands 
WHERE device_id = '330f8cacd4ec197c' 
ORDER BY created_at DESC;
```

## 🔄 Reiniciar Novamente

Para reiniciar o mesmo dispositivo novamente, basta criar um novo comando:

```sql
INSERT INTO reboot_commands (device_id, should_reboot, executed)
VALUES ('330f8cacd4ec197c', true, false);
```

## ⚠️ Importante

- O app precisa ter **Device Admin ativado** para reiniciar
- O dispositivo precisa ter **internet** para verificar o comando
- O comando é verificado a cada **30 segundos**
- Após reiniciar, o comando é marcado como executado automaticamente

## 🐛 Troubleshooting

### O dispositivo não reinicia

1. Verifique se o Device Admin está ativo:
   - Abra o app no dispositivo
   - O app deve solicitar permissão de Device Admin
   - Aceite a permissão

2. Verifique se há internet:
   - O app precisa de conexão para verificar o Supabase

3. Verifique os logs:
   - Use `adb logcat` ou Android Studio
   - Procure por logs com tag `RebootMonitorService`

### O comando não é detectado

1. Verifique se o Device ID está correto
2. Verifique se o comando foi criado corretamente no Supabase
3. Aguarde até 30 segundos (tempo de verificação)

## 📱 Device IDs de Outros Dispositivos

Se você tiver múltiplos dispositivos, cada um terá um Device ID diferente. Para reiniciar outro dispositivo, use o Device ID dele:

```sql
-- Dispositivo 1
INSERT INTO reboot_commands (device_id, should_reboot, executed)
VALUES ('330f8cacd4ec197c', true, false);

-- Dispositivo 2 (exemplo)
INSERT INTO reboot_commands (device_id, should_reboot, executed)
VALUES ('OUTRO_DEVICE_ID_AQUI', true, false);
```

---

**Dica**: Você pode criar um script ou interface web para facilitar o envio de comandos de reiniciar!
