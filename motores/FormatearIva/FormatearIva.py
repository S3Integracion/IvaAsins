#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Motor FormatearIva.
Procesa reporte Amazon (.txt) y base IVA (.csv/.xlsx) para actualizar la base
in-place, generar previsualizacion CSV y un reporte detallado.
"""
import argparse
import csv
import os
import sys
from collections import OrderedDict
from datetime import datetime

BASE_SHEET_NAME = "IVA's Base de Datos"


def _add_vendor():
    here = os.path.dirname(os.path.abspath(__file__))
    vendor = os.path.join(here, 'vendor')
    if os.path.isdir(vendor) and vendor not in sys.path:
        sys.path.insert(0, vendor)


def _load_openpyxl():
    _add_vendor()
    try:
        import openpyxl  # type: ignore
    except Exception as exc:
        raise RuntimeError('No se pudo cargar openpyxl desde la carpeta vendor.') from exc
    return openpyxl


def _detect_delimiter(sample_line):
    candidates = ['\t', ';', ',', '|']
    counts = {d: sample_line.count(d) for d in candidates}
    best = max(counts, key=counts.get)
    if counts[best] == 0:
        return ','
    return best


def _normalize_header(name):
    return str(name or '').strip().lower().replace(' ', '-').replace('_', '-')


def _is_cancelled(status):
    s = (status or '').strip().lower()
    return 'cancel' in s


def _has_tax(value):
    s = (value or '').strip()
    if s == '':
        return False
    try:
        num = float(s.replace(',', ''))
        return num > 0
    except ValueError:
        return True


def _normalize_iva(value):
    s = (value or '').strip().upper()
    if s in ('SI', 'SÍ', 'YES', 'Y', '1', 'TRUE'):
        return 'SI'
    if s in ('NO', 'N', '0', 'FALSE'):
        return 'NO'
    return s


def _read_header_line(path):
    with open(path, 'r', encoding='utf-8-sig', errors='replace') as f:
        line = f.readline()
    return line


def _load_base_csv(base_path):
    header_line = _read_header_line(base_path)
    if not header_line:
        raise ValueError('El CSV base esta vacio.')
    delimiter = _detect_delimiter(header_line)
    trailing_delim = header_line.rstrip('\r\n').endswith(delimiter)

    header_fields = next(csv.reader([header_line], delimiter=delimiter))
    header_map = {_normalize_header(h): i for i, h in enumerate(header_fields) if str(h).strip() != ''}

    if 'asin' not in header_map:
        raise ValueError('No se encontro la columna ASIN en el CSV base.')
    if 'iva' not in header_map:
        raise ValueError('No se encontro la columna IVA en el CSV base.')

    base_map = OrderedDict()
    base_duplicates = []
    total_rows = 0

    with open(base_path, 'r', encoding='utf-8-sig', errors='replace', newline='') as f:
        reader = csv.reader(f, delimiter=delimiter)
        try:
            next(reader)
        except StopIteration:
            return base_map, header_fields, header_map, delimiter, trailing_delim, total_rows, base_duplicates, header_line.rstrip('\r\n')
        for row in reader:
            if not row:
                continue
            total_rows += 1
            if len(row) < len(header_fields):
                row += [''] * (len(header_fields) - len(row))
            asin = row[header_map['asin']].strip()
            iva = row[header_map['iva']].strip()
            if not asin:
                continue
            asin_norm = asin.upper()
            iva_norm = _normalize_iva(iva)
            if asin_norm in base_map:
                base_duplicates.append(asin_norm)
                if base_map[asin_norm]['IVA'] != 'SI' and iva_norm == 'SI':
                    base_map[asin_norm]['IVA'] = 'SI'
                continue
            base_map[asin_norm] = {'ASIN': asin_norm, 'IVA': iva_norm}

    return base_map, header_fields, header_map, delimiter, trailing_delim, total_rows, base_duplicates, header_line.rstrip('\r\n')


def _load_base_xlsx(base_path, sheet_name):
    openpyxl = _load_openpyxl()
    wb = openpyxl.load_workbook(base_path)
    if sheet_name is None:
        sheet_name = BASE_SHEET_NAME
    if sheet_name not in wb.sheetnames:
        names = '|'.join(wb.sheetnames)
        raise ValueError('HOJA_NO_ENCONTRADA|' + names)

    ws = wb[sheet_name]
    header_fields = []
    for col in range(1, ws.max_column + 1):
        header_fields.append(ws.cell(row=1, column=col).value)
    header_map = {_normalize_header(h): i for i, h in enumerate(header_fields) if str(h or '').strip() != ''}

    if 'asin' not in header_map:
        raise ValueError('No se encontro la columna ASIN en la hoja base.')
    if 'iva' not in header_map:
        raise ValueError('No se encontro la columna IVA en la hoja base.')

    base_map = OrderedDict()
    base_duplicates = []
    total_rows = 0
    asin_col = header_map['asin'] + 1
    iva_col = header_map['iva'] + 1

    for row_idx in range(2, ws.max_row + 1):
        asin_val = ws.cell(row=row_idx, column=asin_col).value
        iva_val = ws.cell(row=row_idx, column=iva_col).value
        asin = str(asin_val or '').strip()
        iva = str(iva_val or '').strip()
        if not asin:
            continue
        total_rows += 1
        asin_norm = asin.upper()
        iva_norm = _normalize_iva(iva)
        if asin_norm in base_map:
            base_duplicates.append(asin_norm)
            if base_map[asin_norm]['IVA'] != 'SI' and iva_norm == 'SI':
                base_map[asin_norm]['IVA'] = 'SI'
            continue
        base_map[asin_norm] = {'ASIN': asin_norm, 'IVA': iva_norm}

    return wb, ws, sheet_name, base_map, header_fields, header_map, total_rows, base_duplicates


def _load_reporte_rows(reporte_path):
    header_line = _read_header_line(reporte_path)
    if not header_line:
        raise ValueError('El reporte esta vacio.')
    delimiter = _detect_delimiter(header_line)

    with open(reporte_path, 'r', encoding='utf-8-sig', errors='replace', newline='') as f:
        reader = csv.reader(f, delimiter=delimiter)
        try:
            raw_headers = next(reader)
        except StopIteration:
            raise ValueError('El reporte no tiene encabezados.')

        normalized_headers = [_normalize_header(h) for h in raw_headers]
        header_map = {h: i for i, h in enumerate(normalized_headers)}

        required = ['asin', 'item-tax', 'order-status']
        missing = [r for r in required if r not in header_map]
        if missing:
            raise ValueError('Faltan columnas en el reporte: ' + ', '.join(missing))

        for row in reader:
            if not row:
                continue
            if len(row) < len(raw_headers):
                row += [''] * (len(raw_headers) - len(row))
            yield {
                'asin': row[header_map['asin']].strip(),
                'item-tax': row[header_map['item-tax']].strip(),
                'order-status': row[header_map['order-status']].strip(),
            }


def _write_properties(path, data):
    with open(path, 'w', encoding='utf-8') as f:
        for key, value in data.items():
            f.write(f"{key}={value}\n")


def _write_preview_csv(path, header_fields, header_map, delimiter, trailing_delim, base_map, start_index):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    header_fields = [str(h or '') for h in header_fields]
    header_line = delimiter.join(header_fields)
    if trailing_delim and (not header_fields or header_fields[-1] != ''):
        header_line += delimiter

    with open(path, 'w', encoding='utf-8', newline='') as f:
        f.write(header_line + '\r\n')
        for idx, record in enumerate(base_map.values()):
            if idx < start_index:
                continue
            row_out = [''] * len(header_fields)
            row_out[header_map['asin']] = record['ASIN']
            row_out[header_map['iva']] = record['IVA']
            line = delimiter.join(row_out)
            if trailing_delim and (not row_out or row_out[-1] != ''):
                line += delimiter
            f.write(line + '\r\n')


def _write_report(report_path, base_path, base_type, sheet_name, reporte_path, resumen, added, modified, cancelled_only,
                  base_duplicates, preview_start_index):
    removed_counts = OrderedDict()
    for asin in base_duplicates:
        removed_counts[asin] = removed_counts.get(asin, 0) + 1

    lines = []
    now = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    lines.append('REPORTE IVA PROCESS')
    lines.append(f'Fecha/Hora: {now}')
    lines.append('')
    lines.append('RESUMEN GENERAL')
    lines.append(f'Base: {base_path}')
    lines.append(f'Tipo base: {base_type}')
    if base_type == 'XLSX':
        lines.append(f'Hoja usada: {sheet_name}')
    lines.append(f'Reporte inventario: {reporte_path}')
    lines.append(f'Total filas en reporte: {resumen["total_reporte"]}')
    lines.append(f'Filas canceladas: {resumen["cancelados_filas"]} (ASIN unicos: {resumen["cancelados_asins"]})')
    lines.append(f'Filas sin ASIN: {resumen["sin_asin_filas"]}')
    lines.append(f'Filas duplicadas en reporte: {resumen["duplicados_filas"]}')
    lines.append(f'ASIN unicos procesados: {resumen["asin_unicos_reporte"]}')
    lines.append(f'Agregados nuevos: {resumen["agregados"]}')
    lines.append(f'Modificados (IVA cambiado): {resumen["modificados"]}')
    lines.append(f'Sin cambios (IVA igual): {resumen["sin_cambios"]}')
    lines.append(f'Duplicados en base consolidados: {resumen["consolidados_base"]}')
    lines.append(f'Eliminados de base (filas): {resumen["eliminados_base"]}')
    lines.append(f'Total base antes: {resumen["base_original"]}')
    lines.append(f'Total base despues: {resumen["base_final"]}')
    lines.append(f'Vista previa inicia en fila (sin encabezado): {preview_start_index + 1}')
    lines.append('')

    lines.append('PRODUCTOS AGREGADOS (ASIN,IVA)')
    lines.append('ASIN,IVA')
    for asin, iva in added:
        lines.append(f'{asin},{iva}')
    lines.append('')

    lines.append('PRODUCTOS MODIFICADOS (ASIN,IVA_ANTERIOR,IVA_NUEVO)')
    lines.append('ASIN,IVA_ANTERIOR,IVA_NUEVO')
    for asin, iva_old, iva_new in modified:
        lines.append(f'{asin},{iva_old},{iva_new}')
    lines.append('')

    lines.append('PRODUCTOS NO PROCESADOS (ASIN,MOTIVO)')
    lines.append('ASIN,MOTIVO')
    for asin in sorted(cancelled_only):
        lines.append(f'{asin},CANCELADO')
    if int(resumen['sin_asin_filas']) > 0:
        lines.append(f',SIN_ASIN (filas={resumen["sin_asin_filas"]})')
    lines.append('')

    lines.append('PRODUCTOS ELIMINADOS DE LA BASE (ASIN,ELIMINADOS)')
    lines.append('ASIN,ELIMINADOS')
    for asin, count in removed_counts.items():
        lines.append(f'{asin},{count}')
    lines.append('')

    if base_duplicates:
        lines.append('DUPLICADOS EN BASE CONSOLIDADOS (ASIN)')
        lines.append('ASIN')
        for asin in sorted(set(base_duplicates)):
            lines.append(asin)
        lines.append('')

    os.makedirs(os.path.dirname(report_path), exist_ok=True)
    with open(report_path, 'w', encoding='utf-8') as f:
        f.write('\n'.join(lines))


def main():
    parser = argparse.ArgumentParser(description='Formatear IVA')
    parser.add_argument('--base', required=True, help='CSV/XLSX base con ASIN, IVA')
    parser.add_argument('--reporte', help='Reporte Amazon .txt')
    parser.add_argument('--salida', help='CSV de previsualizacion')
    parser.add_argument('--resumen', help='Archivo resumen (properties)')
    parser.add_argument('--reporte-out', help='Reporte detallado .txt')
    parser.add_argument('--sheet', help='Nombre de hoja para XLSX')
    parser.add_argument('--list-sheets', action='store_true', help='Listar hojas de un XLSX')
    args = parser.parse_args()

    base_path = os.path.abspath(args.base)
    if not os.path.isfile(base_path):
        raise FileNotFoundError('No existe la base: ' + base_path)

    if args.list_sheets:
        ext = os.path.splitext(base_path)[1].lower()
        if ext != '.xlsx':
            raise ValueError('El archivo base no es XLSX.')
        openpyxl = _load_openpyxl()
        wb = openpyxl.load_workbook(base_path, read_only=True)
        for name in wb.sheetnames:
            print(name)
        return 0

    if not args.reporte:
        raise ValueError('Falta --reporte')
    if not args.salida:
        raise ValueError('Falta --salida')

    reporte_path = os.path.abspath(args.reporte)
    salida_path = os.path.abspath(args.salida)
    resumen_path = os.path.abspath(args.resumen) if args.resumen else salida_path + '.resumen'
    reporte_out_path = os.path.abspath(args.reporte_out) if args.reporte_out else None

    if not os.path.isfile(reporte_path):
        raise FileNotFoundError('No existe el reporte: ' + reporte_path)

    ext = os.path.splitext(base_path)[1].lower()
    base_type = 'XLSX' if ext == '.xlsx' else 'CSV'

    if base_type == 'CSV':
        (base_map, header_fields, header_map, base_delim, trailing_delim,
         base_original_rows, base_duplicates, header_line) = _load_base_csv(base_path)
        wb = None
        ws = None
        sheet_name = None
    else:
        wb, ws, sheet_name, base_map, header_fields, header_map, base_original_rows, base_duplicates = _load_base_xlsx(
            base_path, args.sheet)
        base_delim = ';'
        trailing_delim = False

    report_map = OrderedDict()
    duplicate_rows = 0
    total_rows = 0
    cancelled_rows = 0
    cancelled_asins = set()
    no_asin_rows = 0

    for row in _load_reporte_rows(reporte_path):
        total_rows += 1
        status = row['order-status']
        if _is_cancelled(status):
            cancelled_rows += 1
            asin_cancel = row['asin'].strip().upper()
            if asin_cancel:
                cancelled_asins.add(asin_cancel)
            continue

        asin = row['asin']
        if not asin:
            no_asin_rows += 1
            continue
        asin_norm = asin.upper()

        iva_value = 'SI' if _has_tax(row['item-tax']) else 'NO'

        if asin_norm in report_map:
            duplicate_rows += 1
            if report_map[asin_norm] == 'NO' and iva_value == 'SI':
                report_map[asin_norm] = 'SI'
            continue

        report_map[asin_norm] = iva_value

    added = []
    modified = []
    unchanged = 0
    first_new_index = None

    for asin_norm, iva_value in report_map.items():
        if asin_norm in base_map:
            old_iva = base_map[asin_norm]['IVA']
            if old_iva != iva_value:
                base_map[asin_norm]['IVA'] = iva_value
                modified.append((asin_norm, old_iva, iva_value))
            else:
                unchanged += 1
        else:
            if first_new_index is None:
                first_new_index = len(base_map)
            base_map[asin_norm] = {'ASIN': asin_norm, 'IVA': iva_value}
            added.append((asin_norm, iva_value))

    if first_new_index is None:
        first_new_index = 0

    if base_type == 'CSV':
        os.makedirs(os.path.dirname(base_path), exist_ok=True)
        with open(base_path, 'w', encoding='utf-8', newline='') as f:
            f.write(header_line + '\r\n')
            for record in base_map.values():
                row_out = [''] * len(header_fields)
                row_out[header_map['asin']] = record['ASIN']
                row_out[header_map['iva']] = record['IVA']
                line = base_delim.join(row_out)
                if trailing_delim and (not row_out or row_out[-1] != ''):
                    line += base_delim
                f.write(line + '\r\n')
    else:
        max_row = ws.max_row
        max_col = ws.max_column
        for row_idx in range(2, max_row + 1):
            for col in range(1, max_col + 1):
                ws.cell(row=row_idx, column=col, value=None)
        row_idx = 2
        asin_col = header_map['asin'] + 1
        iva_col = header_map['iva'] + 1
        for record in base_map.values():
            ws.cell(row=row_idx, column=asin_col, value=record['ASIN'])
            ws.cell(row=row_idx, column=iva_col, value=record['IVA'])
            row_idx += 1
        wb.save(base_path)

    _write_preview_csv(salida_path, header_fields, header_map, base_delim, trailing_delim, base_map, first_new_index)

    cancelled_only = cancelled_asins - set(report_map.keys())

    resumen_data = {
        'ok': 'true',
        'total_reporte': str(total_rows),
        'duplicados_filas': str(duplicate_rows),
        'cancelados_filas': str(cancelled_rows),
        'cancelados_asins': str(len(cancelled_only)),
        'sin_asin_filas': str(no_asin_rows),
        'asin_unicos_reporte': str(len(report_map)),
        'agregados': str(len(added)),
        'modificados': str(len(modified)),
        'sin_cambios': str(unchanged),
        'consolidados_base': str(len(set(base_duplicates))),
        'eliminados_base': str(len(base_duplicates)),
        'base_original': str(len(base_map) - len(added)),
        'base_final': str(len(base_map)),
        'preview_inicio': str(first_new_index),
    }
    _write_properties(resumen_path, resumen_data)

    if reporte_out_path:
        _write_report(
            reporte_out_path,
            base_path,
            base_type,
            sheet_name,
            reporte_path,
            resumen_data,
            added,
            modified,
            cancelled_only,
            base_duplicates,
            first_new_index,
        )

    print('OK')
    return 0


if __name__ == '__main__':
    try:
        sys.exit(main())
    except Exception as exc:
        sys.stderr.write('ERROR: ' + str(exc) + '\n')
        sys.exit(1)
