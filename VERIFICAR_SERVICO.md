# 🔍 Como Verificar se o Serviço Está Rodando

Se o comando não está sendo detectado, o serviço pode não estar rodando.

## 📱 Verificar no Dispositivo

### 1. Abrir o App

1. Abra o app **MRIT Control** no dispositivo
2. Isso garante que o serviço seja iniciado
3. O serviço deve iniciar automaticamente quando o app abre

### 2. Verificar Device ID

O Device ID deve aparecer no rodapé da tela do app. Verifique se corresponde ao Device ID no Supabase:
- **Device ID no app**: Veja no rodapé da tela
- **Device ID no Supabase**: `7d23a2de2dd3e636`

**IMPORTANTE**: Os Device IDs devem ser **exatamente iguais** (case-sensitive)!

## 💻 Verificar via ADB (Recomendado)

### 1. Conectar Dispositivo

```bash
adb devices
```

### 2. Verificar Logs do Serviço

```bash
adb logcat -c  # Limpa logs anteriores
adb logcat | grep -E "RebootMonitorService|SupabaseManager|DeviceIdManager"
```

### 3. O que Procurar

**Se o serviço estiver rodando, você verá:**
```
RebootMonitorService: === RebootMonitorService INICIADO ===
RebootMonitorService: Device ID: 7d23a2de2dd3e636
RebootMonitorService: Device Admin ativo: true
RebootMonitorService: Iniciando loop de monitoramento...
RebootMonitorService: === Verificação #1 ===
SupabaseManager: === Verificando comando no Supabase ===
```

**Se o serviço NÃO estiver rodando, você NÃO verá essas mensagens.**

### 4. Verificar se Serviço Está Ativo

```bash
adb shell dumpsys activity services | grep RebootMonitorService
```

Se aparecer algo como `RebootMonitorService`, o serviço está rodando.

## 🔄 Forçar Reinício do Serviço

### Opção 1: Reiniciar o App

1. Feche completamente o app (force stop)
2. Abra o app novamente
3. Isso deve iniciar o serviço

### Opção 2: Via ADB

```bash
# Forçar parada do app
adb shell am force-stop com.bootreceiver.app

# Iniciar o app novamente
adb shell am start -n com.bootreceiver.app/.ui.AppSelectionActivity
```

## 🐛 Problemas Comuns

### Device ID Não Confere

**Sintoma**: Serviço roda mas não encontra comandos

**Solução**: 
1. Verifique o Device ID no rodapé do app
2. Compare com o Device ID no Supabase
3. Devem ser **exatamente iguais** (incluindo maiúsculas/minúsculas)

### Serviço Não Inicia

**Sintoma**: Não aparecem logs do RebootMonitorService

**Solução**:
1. Abra o app manualmente
2. Verifique se há erros nos logs
3. Verifique se o app tem permissão de iniciar serviços em background

### Erro de Conexão com Supabase

**Sintoma**: Logs mostram erro ao conectar

**Solução**:
1. Verifique se há internet
2. Verifique se a URL e Key do Supabase estão corretas
3. Verifique se a tabela existe no Supabase

## 📋 Checklist Rápido

- [ ] App está instalado e aberto
- [ ] Device ID no app = Device ID no Supabase (exatamente igual)
- [ ] Logs mostram "RebootMonitorService INICIADO"
- [ ] Logs mostram "Verificando comando no Supabase"
- [ ] Internet está funcionando
- [ ] Device Admin está ativo

## 🔍 Comando Completo para Diagnóstico

```bash
# Limpar logs
adb logcat -c

# Monitorar logs em tempo real
adb logcat | grep -E "RebootMonitorService|SupabaseManager|DeviceIdManager|RebootManager"

# Em outro terminal, criar comando no Supabase e observar os logs
```

---

**Dica**: Se os logs não aparecerem, o serviço provavelmente não está rodando. Abra o app manualmente para iniciá-lo.
