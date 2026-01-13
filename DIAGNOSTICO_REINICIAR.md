# 🔍 Diagnóstico: Dispositivo Não Reinicia

Se o comando foi detectado (`executed: true`) mas o dispositivo não reiniciou, siga este guia de diagnóstico.

## ✅ Status Atual

- ✅ Comando criado no Supabase
- ✅ Comando detectado pelo app (`executed: true`)
- ❌ Dispositivo não reiniciou

## 🔍 Possíveis Causas

### 1. Device Admin Não Está Ativo (Mais Provável)

O app precisa de permissão de Device Admin para reiniciar. Verifique:

**Como verificar:**
1. Abra **Configurações** no dispositivo Android
2. Vá em **Segurança** ou **Administradores do dispositivo**
3. Procure por **MRIT Control** ou **BootReceiver**
4. Verifique se está **ativado/ligado**

**Como ativar:**
1. Abra o app **MRIT Control** no dispositivo
2. O app deve solicitar permissão de Device Admin automaticamente
3. Se não solicitar, o serviço pode não estar rodando

### 2. Serviço Não Está Rodando

O `RebootMonitorService` precisa estar ativo para verificar comandos.

**Como verificar:**
```bash
# Via ADB
adb shell dumpsys activity services | grep RebootMonitorService
```

**Como iniciar:**
1. Abra o app manualmente
2. O serviço deve iniciar automaticamente quando o app abre
3. Verifique os logs para confirmar

### 3. Erro ao Tentar Reiniciar

O app pode ter detectado o comando mas falhado ao reiniciar.

**Verificar logs:**
```bash
# Via ADB
adb logcat | grep -i "RebootManager\|RebootMonitorService"
```

Procure por:
- `Device Admin não está ativo` → Device Admin não está ativado
- `Sem permissão para reiniciar` → Falta permissão
- `Erro ao reiniciar` → Outro erro

## 🛠️ Soluções

### Solução 1: Ativar Device Admin Manualmente

1. Abra **Configurações** no dispositivo
2. Vá em **Segurança** > **Administradores do dispositivo**
3. Encontre **MRIT Control** ou **BootReceiver**
4. **Ative** a opção
5. Tente criar um novo comando de reiniciar

### Solução 2: Reiniciar o App

1. Feche completamente o app
2. Abra o app novamente
3. Isso deve iniciar o `RebootMonitorService`
4. Aguarde alguns segundos
5. Crie um novo comando de reiniciar

### Solução 3: Verificar Logs em Tempo Real

```bash
# Conecte o dispositivo via USB
adb logcat -c  # Limpa logs anteriores
adb logcat | grep -E "RebootManager|RebootMonitorService|DeviceAdmin"
```

Depois, crie um novo comando e observe os logs.

### Solução 4: Criar Novo Comando

Após verificar/ativar o Device Admin, crie um novo comando:

```sql
INSERT INTO reboot_commands (device_id, should_reboot, executed)
VALUES ('330f8cacd4ec197c', true, false);
```

## 📋 Checklist de Verificação

Marque cada item:

- [ ] Device Admin está ativo nas configurações do Android
- [ ] App está instalado e funcionando
- [ ] Dispositivo tem conexão com internet
- [ ] Serviço `RebootMonitorService` está rodando
- [ ] Logs não mostram erros de permissão
- [ ] Novo comando foi criado após ativar Device Admin

## 🔄 Teste Passo a Passo

1. **Verifique Device Admin:**
   ```
   Configurações > Segurança > Administradores do dispositivo
   → MRIT Control deve estar ATIVO
   ```

2. **Abra o app:**
   - Isso garante que o serviço está rodando

3. **Verifique logs (se possível):**
   ```bash
   adb logcat | grep RebootMonitorService
   ```
   - Deve mostrar: "Verificando comando de reiniciar..."
   - Deve mostrar: "Comando encontrado! Executando..."

4. **Crie novo comando:**
   ```sql
   INSERT INTO reboot_commands (device_id, should_reboot, executed)
   VALUES ('330f8cacd4ec197c', true, false);
   ```

5. **Aguarde até 30 segundos:**
   - O app verifica a cada 30 segundos
   - O dispositivo deve reiniciar

## 🐛 Logs Esperados

Se tudo estiver funcionando, você deve ver nos logs:

```
RebootMonitorService: Verificando comando de reiniciar...
SupabaseManager: Comando encontrado: 1
RebootMonitorService: Comando de reiniciar encontrado! Executando...
SupabaseManager: Comando marcado como executado: 1
RebootManager: Reiniciando dispositivo...
RebootManager: Comando de reiniciar enviado via DevicePolicyManager
```

Se Device Admin não estiver ativo:

```
RebootManager: Device Admin não está ativo. Não é possível reiniciar.
RebootMonitorService: Falha ao reiniciar. Verifique se Device Admin está ativo.
```

## ⚠️ Limitações Conhecidas

1. **Android TV/Stick**: Alguns dispositivos podem ter restrições adicionais
2. **Fabricantes**: Samsung, Xiaomi, etc. podem ter políticas diferentes
3. **Root**: Em alguns casos, pode ser necessário root para reiniciar

## 📞 Próximos Passos

Se após seguir todos os passos o dispositivo ainda não reiniciar:

1. Compartilhe os logs do `adb logcat`
2. Confirme se Device Admin está ativo
3. Verifique a versão do Android (deve ser API 24+)
4. Teste em outro dispositivo se possível

---

**Dica**: O mais comum é o Device Admin não estar ativo. Sempre verifique isso primeiro!
