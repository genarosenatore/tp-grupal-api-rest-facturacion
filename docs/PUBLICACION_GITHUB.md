# Publicación de la entrega académica

## Versión revisada

Desde la raíz del proyecto, con Python 3.10 o posterior:

```powershell
.\scripts\preparar-entrega.ps1
# Alternativa en otros sistemas:
python scripts/preparar-publicacion.py
```

Genera `entrega/github-publico/`, `entrega/TP-Facturacion-publico.zip` y su SHA256.
La carpeta contiene los archivos que deben quedar en la raíz del repositorio.
El ZIP es una alternativa para entregar fuentes; no es necesario subirlo a GitHub.
Si ya existe una entrega pública, el script solicita conservarla o renombrarla
antes de generar otra, para no sobrescribir trabajo ni historial Git.

Para revisar sin generar archivos:

```powershell
.\scripts\preparar-entrega.ps1 -SoloVerificar
```

Se usa una lista de carpetas y extensiones permitidas. Se copian fuentes,
pruebas, ejemplos ficticios, migraciones, documentación Markdown y el frontend
compilado referenciado por el HTML actual. Se excluyen las versiones antiguas
de sus assets, JAR, ZIP, PDF, imágenes de evidencia, respuestas locales,
dependencias, logs, bases, `.local` y cualquier historial `.git`.

La revisión detecta claves/certificados incrustados, ciertos tokens, credenciales
literales y rutas de usuario. También compara con contraseñas, CUIT, CAE y tickets
disponibles en la configuración privada de este equipo, sin imprimir sus valores.
Es una comprobación preventiva; si se agregan datos personales nuevos, deben
revisarse antes de publicar aunque no coincidan con esos patrones.
Los CUIT y CAE ficticios de las pruebas se conservan como fixtures.

## Subir a GitHub

Crear en la cuenta del alumno un repositorio **público** vacío. Desde
`entrega/github-publico`, ejecutar lo siguiente, sustituyendo la URL por la real:

```powershell
git init -b main
git add .
git status --short
git commit -m "Entrega del TP de facturación"
git remote add origin https://github.com/USUARIO/REPOSITORIO.git
git push -u origin main
```

Como alternativa, cargar el contenido de `github-publico` desde la interfaz web
de GitHub, incluyendo `.gitignore`, `.gitattributes` y `frontend/.env.example`.
Publicar desde esta carpeta evita incorporar bases y evidencia local por error.

No agregar contraseñas, certificados ni datos reales para que el profesor pueda
ejecutarlo: cada instalación configura una contraseña de base propia. ARCA queda
deshabilitada al inicio. El profesor puede ejecutar las pruebas H2 y usar el
modo PostgreSQL sin identidad fiscal personal.

## Enlace para el campus

Abrir la página del repositorio sin iniciar sesión y comprobar que se ven el
README, `pom.xml`, `src`, `frontend`, `sql`, `examples`, `scripts` y `docs`.
El enlace a entregar es `https://github.com/USUARIO/REPOSITORIO`.
Es un enlace al código; ejecutar la aplicación requiere seguir el README.

Texto sugerido para la entrega:

> Repositorio público del TP de facturación de ventas. Incluye backend Spring Boot,
> frontend TypeScript, migraciones PostgreSQL, pruebas e instrucciones de ejecución.
> Los datos de demostración son ficticios y las credenciales personales están excluidas.

Esta preparación no crea un repositorio remoto ni sube contenido al campus.
