#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Motor FormatearIva.
Procesa reporte Amazon (.txt) y base IVA (.csv) para generar Asins_Taxes.csv.
"""
import argparse
import csv
import os
import sys
from collections import OrderedDict


def _detect_delimiter(sample_line):
    candidates = ['\t', ';', ',', '|']
    counts = {d: sample_line.count(d) for d in candidates}
    best = max(counts, key=counts.get)
    if counts[best] == 0:
        return ','
    return best


def _normalize_header(name):
    return name.strip().lower().replace(' ', '-').replace('_', '-')


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


def _read_header_line(path):
    with open(path, 'r', encoding='utf-8-sig', errors='replace') as f:
        line = f.readline()
    return line


def _load_base_asins(base_path):
    header_line = _read_header_line(base_path)
    if not header_line:
        raise ValueError('El CSV base está vacío.')
    delimiter = _detect_delimiter(header_line)
    trailing_delim = header_line.rstrip('\r\n').endswith(delimiter)

    header_fields = next(csv.reader([header_line], delimiter=delimiter))
    header_map = { _normalize_header(h): i for i, h in enumerate(header_fields) if h.strip() != '' }

    if 'asin' not in header_map:
        raise ValueError('No se encontró la columna ASIN en el CSV base.')
    if 'sku' not in header_map:
        raise ValueError('No se encontró la columna SKU en el CSV base.')
    if 'iva' not in header_map:
        raise ValueError('No se encontró la columna IVA en el CSV base.')

    asins = set()
    with open(base_path, 'r', encoding='utf-8-sig', errors='replace', newline='') as f:
        reader = csv.reader(f, delimiter=delimiter)
        try:
            next(reader)
        except StopIteration:
            return asins, header_fields, header_map, delimiter, trailing_delim
        for row in reader:
            if not row:
                continue
            if len(row) < len(header_fields):
                row += [''] * (len(header_fields) - len(row))
            asin = row[header_map['asin']].strip()
            if asin:
                asins.add(asin.upper())

    return asins, header_fields, header_map, delimiter, trailing_delim


def _load_reporte_rows(reporte_path):
    header_line = _read_header_line(reporte_path)
    if not header_line:
        raise ValueError('El reporte está vacío.')
    delimiter = _detect_delimiter(header_line)

    with open(reporte_path, 'r', encoding='utf-8-sig', errors='replace', newline='') as f:
        reader = csv.reader(f, delimiter=delimiter)
        try:
            raw_headers = next(reader)
        except StopIteration:
            raise ValueError('El reporte no tiene encabezados.')

        normalized_headers = [_normalize_header(h) for h in raw_headers]
        header_map = {h: i for i, h in enumerate(normalized_headers)}

        required = ['asin', 'sku', 'item-tax', 'order-status']
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
                'sku': row[header_map['sku']].strip(),
                'item-tax': row[header_map['item-tax']].strip(),
                'order-status': row[header_map['order-status']].strip(),
            }


def _write_properties(path, data):
    with open(path, 'w', encoding='utf-8') as f:
        for key, value in data.items():
            f.write(f"{key}={value}\n")


def main():
    parser = argparse.ArgumentParser(description='Formatear IVA')
    parser.add_argument('--base', required=True, help='CSV base con ASIN, SKU, IVA')
    parser.add_argument('--reporte', required=True, help='Reporte Amazon .txt')
    parser.add_argument('--salida', required=True, help='CSV de salida')
    parser.add_argument('--resumen', required=False, help='Archivo resumen (properties)')
    args = parser.parse_args()

    base_path = os.path.abspath(args.base)
    reporte_path = os.path.abspath(args.reporte)
    salida_path = os.path.abspath(args.salida)
    resumen_path = os.path.abspath(args.resumen) if args.resumen else salida_path + '.resumen'

    if not os.path.isfile(base_path):
        raise FileNotFoundError('No existe el CSV base: ' + base_path)
    if not os.path.isfile(reporte_path):
        raise FileNotFoundError('No existe el reporte: ' + reporte_path)

    base_asins, header_fields, header_map, base_delim, trailing_delim = _load_base_asins(base_path)

    output_rows = OrderedDict()
    duplicates = []
    skipped_cancelled = 0
    skipped_base = 0
    processed = 0

    for row in _load_reporte_rows(reporte_path):
        status = row['order-status']
        if _is_cancelled(status):
            skipped_cancelled += 1
            continue

        asin = row['asin']
        if not asin:
            continue
        asin_norm = asin.upper()
        if asin_norm in base_asins:
            skipped_base += 1
            continue

        iva_value = 'SI' if _has_tax(row['item-tax']) else 'NO'
        sku_value = row['sku']

        if asin_norm in output_rows:
            existing = output_rows[asin_norm]
            if existing['IVA'] != iva_value:
                duplicates.append(asin)
                if iva_value == 'SI':
                    output_rows[asin_norm] = {'ASIN': asin, 'SKU': sku_value, 'IVA': iva_value}
            else:
                duplicates.append(asin)
            continue

        output_rows[asin_norm] = {'ASIN': asin, 'SKU': sku_value, 'IVA': iva_value}
        processed += 1

    os.makedirs(os.path.dirname(salida_path), exist_ok=True)
    with open(salida_path, 'w', encoding='utf-8', newline='') as f:
        header_line = base_delim.join(header_fields)
        if trailing_delim and (not header_fields or header_fields[-1] != ''):
            header_line += base_delim
        f.write(header_line + '\r\n')

        for record in output_rows.values():
            row_out = [''] * len(header_fields)
            row_out[header_map['asin']] = record['ASIN']
            row_out[header_map['sku']] = record['SKU']
            row_out[header_map['iva']] = record['IVA']
            line = base_delim.join(row_out)
            if trailing_delim and (not row_out or row_out[-1] != ''):
                line += base_delim
            f.write(line + '\r\n')

    resumen_data = {
        'ok': 'true',
        'procesados': str(processed),
        'saltados_cancelados': str(skipped_cancelled),
        'saltados_base': str(skipped_base),
        'duplicados': str(len(set(duplicates))),
        'duplicados_asin': ','.join(sorted(set(duplicates)))
    }
    _write_properties(resumen_path, resumen_data)

    print('OK')
    return 0


if __name__ == '__main__':
    try:
        sys.exit(main())
    except Exception as exc:
        sys.stderr.write('ERROR: ' + str(exc) + '\n')
        sys.exit(1)
