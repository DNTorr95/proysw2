import { Route, Routes } from "react-router-dom";
import Layout from "./components/Layout";
import RutaProtegida from "./components/RutaProtegida";
import LoginPage from "./pages/LoginPage";
import InicioPage from "./pages/InicioPage";
import CuentaPage from "./pages/CuentaPage";
import UsuariosPage from "./pages/UsuariosPage";
import RolesPage from "./pages/RolesPage";
import AuditoriaPage from "./pages/AuditoriaPage";
import TramitesPage from "./pages/tramites/TramitesPage";
import RegistrarTramitePage from "./pages/tramites/RegistrarTramitePage";
import TramiteDetallePage from "./pages/tramites/TramiteDetallePage";
import CorrespondenciaPage from "./pages/CorrespondenciaPage";
import ExpedientesPage from "./pages/ExpedientesPage";
import DocumentosPage from "./pages/DocumentosPage";
import ConsultasPage from "./pages/ConsultasPage";
import EjecutarRpaPage from "./pages/rpa/EjecutarRpaPage";
import RevisionRpaPage, { ListaRevisionRpaPage } from "./pages/rpa/RevisionRpaPage";
import HistorialRpaPage from "./pages/rpa/HistorialRpaPage";
import AcademicoPage from "./pages/academico/AcademicoPage";
import InstitucionalPage from "./pages/institucional/InstitucionalPage";
import PoaPage from "./pages/institucional/PoaPage";
import EventosPage from "./pages/institucional/EventosPage";
import TransferenciaAutoridadPage from "./pages/TransferenciaAutoridadPage";
import DashboardPage from "./pages/reportes/DashboardPage";
import AlertasPage from "./pages/reportes/AlertasPage";
import ReportesPage from "./pages/reportes/ReportesPage";
import OcrPage from "./pages/reportes/OcrPage";
import BusquedaPage from "./pages/reportes/BusquedaPage";
import { useAuth } from "./auth/AuthContext";
import { PERMISOS_BUSQUEDA } from "./lib/menu";

/** CU-26: quien tiene VER_DASHBOARD ve los indicadores como página de inicio. */
function Inicio() {
  return useAuth().tienePermiso("VER_DASHBOARD") ? <DashboardPage /> : <InicioPage />;
}

const PERMISOS_RPA = ["CONSULTAR_RPA", "REVISAR_RPA", "AUTORIZAR_ENVIO_RPA", "GESTIONAR_REEJECUCION_RPA"];
const PERMISOS_TRAMITES = ["REGISTRAR_TRAMITE", "CONSULTAR_TRAMITES", "EJECUTAR_ACTUACION_TRAMITE", "APROBAR_RECHAZAR_TRAMITE"];

function NoEncontrado() {
  return <p className="mt-16 text-center text-slate-500">La página solicitada no existe.</p>;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<RutaProtegida><Layout /></RutaProtegida>}>
        <Route index element={<Inicio />} />
        <Route path="cuenta" element={<CuentaPage />} />
        <Route path="usuarios" element={<RutaProtegida permisos={["GESTIONAR_USUARIOS"]}><UsuariosPage /></RutaProtegida>} />
        <Route path="roles" element={<RutaProtegida permisos={["GESTIONAR_ROLES_PERMISOS"]}><RolesPage /></RutaProtegida>} />
        <Route path="auditoria" element={<RutaProtegida permisos={["CONSULTAR_AUDITORIA"]}><AuditoriaPage /></RutaProtegida>} />
        <Route path="tramites" element={<RutaProtegida permisos={PERMISOS_TRAMITES}><TramitesPage /></RutaProtegida>} />
        <Route path="tramites/nuevo" element={<RutaProtegida permisos={["REGISTRAR_TRAMITE"]}><RegistrarTramitePage /></RutaProtegida>} />
        <Route path="tramites/:id" element={<RutaProtegida permisos={PERMISOS_TRAMITES}><TramiteDetallePage /></RutaProtegida>} />
        <Route path="correspondencia" element={<RutaProtegida permisos={["GESTIONAR_CORRESPONDENCIA", "CONSULTAR_CORRESPONDENCIA", "SEGUIMIENTO_CORRESPONDENCIA"]}><CorrespondenciaPage /></RutaProtegida>} />
        <Route path="expedientes" element={<RutaProtegida permisos={["GESTIONAR_EXPEDIENTES", "CONSULTAR_EXPEDIENTES"]}><ExpedientesPage /></RutaProtegida>} />
        <Route path="documentos" element={<RutaProtegida permisos={["GESTIONAR_DOCUMENTOS"]}><DocumentosPage /></RutaProtegida>} />
        <Route path="consultas" element={<RutaProtegida permisos={["CONSULTAR_FILTROS"]}><ConsultasPage /></RutaProtegida>} />
        <Route path="rpa/ejecutar" element={<RutaProtegida permisos={["EJECUTAR_RPA"]}><EjecutarRpaPage /></RutaProtegida>} />
        <Route path="rpa/revision" element={<RutaProtegida permisos={PERMISOS_RPA}><ListaRevisionRpaPage /></RutaProtegida>} />
        <Route path="rpa/revision/:id" element={<RutaProtegida permisos={PERMISOS_RPA}><RevisionRpaPage /></RutaProtegida>} />
        <Route path="rpa/historial" element={<RutaProtegida permisos={PERMISOS_RPA}><HistorialRpaPage /></RutaProtegida>} />
        <Route path="academico" element={<RutaProtegida permisos={["GESTIONAR_PLANIFICACION", "GESTIONAR_MATERIAS_GRUPOS", "GESTIONAR_MIGRACIONES", "GESTIONAR_CARGA_DOCENTE", "CONSULTAR_ACADEMICO"]}><AcademicoPage /></RutaProtegida>} />
        <Route path="institucional" element={<RutaProtegida permisos={["GESTIONAR_TITULADOS", "GESTIONAR_TRABAJOS_GRADO", "CONSULTAR_INSTITUCIONAL"]}><InstitucionalPage /></RutaProtegida>} />
        <Route path="poa" element={<RutaProtegida permisos={["GESTIONAR_POA", "CONSULTAR_POA"]}><PoaPage /></RutaProtegida>} />
        <Route path="eventos" element={<RutaProtegida permisos={["GESTIONAR_EVENTOS", "CONSULTAR_INSTITUCIONAL", "CONSULTAR_POA"]}><EventosPage /></RutaProtegida>} />
        <Route path="autoridad" element={<RutaProtegida permisos={["TRANSFERIR_AUTORIDAD"]}><TransferenciaAutoridadPage /></RutaProtegida>} />
        <Route path="ocr" element={<RutaProtegida permisos={["PROCESAR_OCR", "VALIDAR_OCR"]}><OcrPage /></RutaProtegida>} />
        <Route path="busqueda" element={<RutaProtegida permisos={PERMISOS_BUSQUEDA}><BusquedaPage /></RutaProtegida>} />
        <Route path="alertas" element={<RutaProtegida permisos={["CONSULTAR_ALERTAS", "ATENDER_ALERTAS"]}><AlertasPage /></RutaProtegida>} />
        <Route path="reportes" element={<RutaProtegida permisos={["GENERAR_REPORTES"]}><ReportesPage /></RutaProtegida>} />
        <Route path="*" element={<NoEncontrado />} />
      </Route>
    </Routes>
  );
}
