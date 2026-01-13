#!/usr/bin/env python3
"""
Script para enviar comandos de reiniciar dispositivo via Supabase
"""

import requests
import json
import sys
from datetime import datetime

# Configurações do Supabase
SUPABASE_URL = "https://kihyhoqbrkwbfudttevo.supabase.co"
SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImtpaHlob3Ficmt3YmZ1ZHR0ZXZvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3MTU1NTUwMjcsImV4cCI6MjAzMTEzMTAyN30.XtBTlSiqhsuUIKmhAMEyxofV-dRst7240n912m4O4Us"

def enviar_comando_reboot(device_id):
    """Envia comando de reiniciar para um dispositivo"""
    url = f"{SUPABASE_URL}/rest/v1/reboot_commands"
    headers = {
        "apikey": SUPABASE_KEY,
        "Authorization": f"Bearer {SUPABASE_KEY}",
        "Content-Type": "application/json",
        "Prefer": "return=representation"
    }
    
    data = {
        "device_id": device_id,
        "should_reboot": True,
        "executed": False
    }
    
    try:
        print(f"📤 Enviando comando de reiniciar para dispositivo: {device_id}")
        response = requests.post(url, headers=headers, json=data)
        response.raise_for_status()
        
        resultado = response.json()
        print(f"✅ Comando enviado com sucesso!")
        print(f"   ID do comando: {resultado[0].get('id')}")
        print(f"   Device ID: {resultado[0].get('device_id')}")
        print(f"   Criado em: {resultado[0].get('created_at')}")
        return True
    except requests.exceptions.RequestException as e:
        print(f"❌ Erro ao enviar comando: {e}")
        if hasattr(e, 'response') and e.response is not None:
            try:
                print(f"   Detalhes: {e.response.json()}")
            except:
                print(f"   Detalhes: {e.response.text}")
        return False

def listar_comandos_pendentes(device_id=None):
    """Lista comandos pendentes (não executados)"""
    url = f"{SUPABASE_URL}/rest/v1/reboot_commands"
    headers = {
        "apikey": SUPABASE_KEY,
        "Authorization": f"Bearer {SUPABASE_KEY}"
    }
    
    params = {
        "should_reboot": "eq.true",
        "executed": "eq.false",
        "order": "created_at.desc"
    }
    
    if device_id:
        params["device_id"] = f"eq.{device_id}"
    
    try:
        response = requests.get(url, headers=headers, params=params)
        response.raise_for_status()
        comandos = response.json()
        
        if comandos:
            print(f"\n📋 Comandos pendentes encontrados: {len(comandos)}")
            for cmd in comandos:
                created_at = cmd.get('created_at', 'N/A')
                print(f"   - ID: {cmd.get('id')}")
                print(f"     Device: {cmd.get('device_id')}")
                print(f"     Criado: {created_at}")
                print()
        else:
            print("✅ Nenhum comando pendente")
        
        return comandos
    except requests.exceptions.RequestException as e:
        print(f"❌ Erro ao listar comandos: {e}")
        return []

def verificar_status_comando(device_id):
    """Verifica status dos comandos de um dispositivo"""
    url = f"{SUPABASE_URL}/rest/v1/reboot_commands"
    headers = {
        "apikey": SUPABASE_KEY,
        "Authorization": f"Bearer {SUPABASE_KEY}"
    }
    
    params = {
        "device_id": f"eq.{device_id}",
        "order": "created_at.desc",
        "limit": "10"
    }
    
    try:
        response = requests.get(url, headers=headers, params=params)
        response.raise_for_status()
        comandos = response.json()
        
        if comandos:
            print(f"\n📊 Últimos comandos para dispositivo: {device_id}")
            print("=" * 60)
            for cmd in comandos:
                status = "✅ Executado" if cmd.get('executed') else "⏳ Pendente"
                print(f"ID: {cmd.get('id')}")
                print(f"Status: {status}")
                print(f"Criado: {cmd.get('created_at')}")
                if cmd.get('executed_at'):
                    print(f"Executado em: {cmd.get('executed_at')}")
                print("-" * 60)
        else:
            print(f"ℹ️ Nenhum comando encontrado para dispositivo: {device_id}")
        
        return comandos
    except requests.exceptions.RequestException as e:
        print(f"❌ Erro ao verificar status: {e}")
        return []

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("=" * 60)
        print("Script para Enviar Comandos de Reiniciar Dispositivo")
        print("=" * 60)
        print("\nUso:")
        print("  python enviar_comando_reboot.py <device_id>          # Enviar comando")
        print("  python enviar_comando_reboot.py --list               # Listar pendentes")
        print("  python enviar_comando_reboot.py --list <device_id>   # Listar pendentes de um dispositivo")
        print("  python enviar_comando_reboot.py --status <device_id> # Ver status de comandos")
        print("\nExemplo:")
        print("  python enviar_comando_reboot.py abc123def456789")
        print("  python enviar_comando_reboot.py --status abc123def456789")
        sys.exit(1)
    
    if sys.argv[1] == "--list":
        device_id = sys.argv[2] if len(sys.argv) > 2 else None
        listar_comandos_pendentes(device_id)
    elif sys.argv[1] == "--status":
        if len(sys.argv) < 3:
            print("❌ Erro: Forneça o device_id para verificar status")
            sys.exit(1)
        verificar_status_comando(sys.argv[2])
    else:
        device_id = sys.argv[1]
        if enviar_comando_reboot(device_id):
            print("\n" + "=" * 60)
            print("⏳ Aguarde até 30 segundos para o dispositivo processar o comando...")
            print("=" * 60)
